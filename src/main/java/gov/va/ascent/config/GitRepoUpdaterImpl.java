package gov.va.ascent.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.cloud.config.monitor.PropertyPathNotification;
import org.springframework.cloud.config.server.environment.JGitEnvironmentRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.xml.ws.Response;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class GitRepoUpdaterImpl implements GitRepoUpdater,ApplicationEventPublisherAware, ApplicationContextAware {

    private ApplicationEventPublisher applicationEventPublisher;

    private String contextId = UUID.randomUUID().toString();

    @Value("${BASE_DIR:../configrepo}")
    private String gitRepoPath;

    @Value("${git.access_token}")
    private String gitAccessToken;

    private String[] branches = {"master", "development", "spring-cloud-bus-poc"};

    @Override
    public void setApplicationContext(ApplicationContext applicationContext)
            throws BeansException {
        this.contextId = applicationContext.getId();
    }

    @Override
    public void setApplicationEventPublisher(
            ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GitRepoUpdater.class);

    final private Map<String, RevCommit> mostRecentCommitByBranchName = new HashMap<>();
    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JGitEnvironmentRepository environmentRepository;

    @Override
    public void compareCommits() throws IOException, GitAPIException{

        CredentialsProvider credProvider = environmentRepository.getGitCredentialsProvider();

        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder.setGitDir(new File(gitRepoPath + "/.git"))
                .setMustExist(true)
                .readEnvironment() // scan environment GIT_* variables
                .build()) {
            Git git = new Git(repository);

            ObjectReader reader = git.getRepository().newObjectReader();
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            ObjectId oldTree = git.getRepository().resolve( "b6f87d9^{tree}" );
            oldTreeIter.reset( reader, oldTree );
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            ObjectId newTree = git.getRepository().resolve( "19c7767^{tree}" );
            newTreeIter.reset( reader, newTree );

            DiffFormatter diffFormatter = new DiffFormatter( DisabledOutputStream.INSTANCE );
            diffFormatter.setRepository( git.getRepository() );
            List<DiffEntry> entries = diffFormatter.scan( oldTreeIter, newTreeIter );

            for( DiffEntry entry : entries ) {
                LOGGER.info( entry.toString() );
            }
        }
    }

    @Override
//    @Scheduled(cron = "0 0/5 * * * *")
    public void updateRepo() throws IOException, GitAPIException{

        CredentialsProvider credProvider = environmentRepository.getGitCredentialsProvider();

        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder.setGitDir(new File(gitRepoPath + "/.git"))
                .setMustExist(true)
                .readEnvironment() // scan environment GIT_* variables
                .build()) {
            final Git git = new Git(repository);
            final List<Ref> branches = Collections.unmodifiableList(git.branchList().call());
            LOGGER.error("Got the repo opened: " + repository.getFullBranch());

            if(mostRecentCommitByBranchName.isEmpty()){
                firstCheckForUpdates(git, credProvider, repository);
            } else if(mostRecentCommitByBranchName.size() < branches.size()){
                checkForNewLocalBranchCheckouts(git);
            }
            gitPullLocalBranches(git, credProvider, branches);

            Set<String> paths = checkForUpdates(repository, git);

            notifyAppsOfExternalConfigChanges(paths);
        }

    }

    @Override
//    @Scheduled(cron = "0 0/5 * * * *")
    public void updateRepoViaGithub() throws IOException {
        LOGGER.info("Update Repo Called");
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("Authorization", "token " + gitAccessToken);
        HttpEntity<String> request = new HttpEntity<>(httpHeaders);
        RestTemplate restTemplate = new RestTemplate();
        ArrayList<String> commits = new ArrayList<>();
        Set<String> paths = new LinkedHashSet<>();

        for (String branch : branches) {
            ResponseEntity<String> result = restTemplate.exchange("https://api.github.com/repos/department-of-veterans-affairs/ascent-ext-configs/commits?sha=spring-cloud-bus-poc&since=2017-11-20T20:45:00Z", HttpMethod.GET, request, String.class);
            ArrayNode nodes = objectMapper.readValue(result.getBody(), ArrayNode.class);
            for (JsonNode node : nodes) {
                commits.add(node.get("sha").asText());
            }

        }

        for (String sha : commits) {
            ResponseEntity<String> result = restTemplate.exchange("https://api.github.com/repos/department-of-veterans-affairs/ascent-ext-configs/commits?sha=" + sha, HttpMethod.GET, request, String.class);
            ArrayNode nodes = objectMapper.readValue(result.getBody(), ArrayNode.class);
            for (JsonNode node : nodes) {

            }
        }

        LOGGER.info(Arrays.toString(commits.toArray()));
    }

    private Set<String> guessServiceName(String path) {
        Set<String> services = new LinkedHashSet<>();
        if (path != null) {
            String stem = StringUtils
                    .stripFilenameExtension(StringUtils.getFilename(StringUtils.cleanPath(path)));
            // TODO: correlate with service registry
            int index = stem.indexOf("-");
            while (index >= 0) {
                String name = stem.substring(0, index);
                String profile = stem.substring(index + 1);
                if ("application".equals(name)) {
                    services.add("*:" + profile);
                }
                else if (!name.startsWith("application")) {
                    services.add(name + ":" + profile);
                }
                index = stem.indexOf("-", index + 1);
            }
            String name = stem;
            if ("application".equals(name)) {
                services.add("*");
            }
            else if (!name.startsWith("application")) {
                services.add(name);
            }
        }
        return services;
    }

    private void firstCheckForUpdates(Git git, CredentialsProvider credProvider, Repository repository) throws IOException, GitAPIException{
        // first run of updating external config repo

        final List<Ref> branches = Collections.unmodifiableList(git.branchList().call());
        for(Ref branch: branches){
            git.checkout().setName(branch.getName()).call();
            git.pull().setCredentialsProvider(credProvider).call();
            Iterable<RevCommit> commits = git.log().add(repository.resolve(Constants.HEAD)).setMaxCount(1).call();
            LOGGER.info("Branch Name: " + branch.getName());
            for(RevCommit newestCommit: commits){
                LOGGER.info("Commit sha: " + newestCommit.toObjectId());
                mostRecentCommitByBranchName.put(branch.getName(), newestCommit);
            }

        }
    }

    private void checkForNewLocalBranchCheckouts(Git git) throws IOException, GitAPIException{
        final List<Ref> branches = Collections.unmodifiableList(git.branchList().call());
        for (Ref branch : branches){
            if(!mostRecentCommitByBranchName.containsKey(branch.getName())){
                final RevWalk revWalk = new RevWalk(git.getRepository());
                RevCommit commit = revWalk.parseCommit(branch.getObjectId());
                mostRecentCommitByBranchName.put(branch.getName(), commit);
                revWalk.close();
            }
        }
    }

    private Set<String> checkForUpdates(Repository repository, Git git) throws IOException, GitAPIException{
        final Set<String> paths = new HashSet<>();

        RevWalk revWalk = new RevWalk(repository);

        final List<Ref> branches = Collections.unmodifiableList(git.branchList().call());

        for(Ref branch : branches){
            LOGGER.info("Branch ID: " + branch.getName());
            Iterable<RevCommit> commits = git.log().addRange(mostRecentCommitByBranchName.get(branch.getName()),
                    repository.resolve(Constants.HEAD)).call();
            for(RevCommit commit : commits){
                LOGGER.info("Commit ID: " + commit.toObjectId());
                if(mostRecentCommitByBranchName.get(branch.getName()) != null &&
                        !mostRecentCommitByBranchName.get(branch.getName()).toObjectId().equals(commit.toObjectId())){
                    try (ObjectReader reader = repository.newObjectReader()) {
                        CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                        ObjectId oldTree = git.getRepository().resolve( mostRecentCommitByBranchName.get(branch.getName()).toObjectId().toString().substring(0,6)+"^{tree}" );
                        oldTreeIter.reset(reader, oldTree);
                        CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                        ObjectId newTree = git.getRepository().resolve( commit.toObjectId().toString().substring(0,6)+"^{tree}" );
                        newTreeIter.reset(reader, newTree);

                        DiffFormatter diffFormatter = new DiffFormatter( DisabledOutputStream.INSTANCE );
                        diffFormatter.setRepository( git.getRepository() );
                        List<DiffEntry> entries = diffFormatter.scan( oldTreeIter, newTreeIter );

                        for( DiffEntry entry : entries ) {
                            LOGGER.info( entry.toString() );
                            paths.add(entry.getNewPath());
                        }
                        mostRecentCommitByBranchName.put(branch.getName(), commit);
//                        List<DiffEntry> diffs = git.diff()
//                                .setNewTree(newTreeIter)
//                                .setOldTree(oldTreeIter)
//                                .call();
//                        for (DiffEntry entry : diffs) {
//                            paths.add(entry.getNewPath());
//                            LOGGER.info("NEW PATH: " + entry.getNewPath());
//                            LOGGER.info("Entry : " + entry);
//                        }
                    }
                }
            }
//            RevCommit commit = revWalk.parseCommit(branch.getObjectId());
//            LOGGER.error("Start-commit:" + commit);
//            revWalk.markStart(commit);
//            RevCommit updatedCommit = commit;
//            for(RevCommit rev : revWalk){
//                updatedCommit = rev;
//                LOGGER.error("Commit: " + rev);
//                if(mostRecentCommitByBranchName.get(branch.getName()) != null &&
//                        mostRecentCommitByBranchName.get(branch.getName()).toObjectId().equals(rev.toObjectId())){
//                    break;
//                } else {
//                    try (ObjectReader reader = repository.newObjectReader()) {
//                        CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
//                        oldTreeIter.reset(reader, mostRecentCommitByBranchName.get(branch.getName()).getTree());
//                        CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
//                        newTreeIter.reset(reader, rev.getTree());
//
//                        List<DiffEntry> diffs = git.diff()
//                                .setNewTree(newTreeIter)
//                                .setOldTree(oldTreeIter)
//                                .call();
//                        for (DiffEntry entry : diffs) {
//                            paths.add(entry.getNewPath());
//                            LOGGER.info("NEW PATH: " + entry.getNewPath());
//                            LOGGER.info("Entry : " + entry);
//                        }
//                    }
//                }
//            }
//            mostRecentCommitByBranchName.put(branch.getName(), updatedCommit);
        }
        revWalk.close();
        return paths;
    }

    private void notifyAppsOfExternalConfigChanges(Set<String> paths){
        PropertyPathNotification propertyPathNotification = new PropertyPathNotification();

        if(!paths.isEmpty()) {
            propertyPathNotification.setPaths(paths.toArray(new String[0]));
        } else {
            propertyPathNotification.setPaths(new String[0]);
        }

        Set<String> services = new LinkedHashSet<>();

        for (String path : propertyPathNotification.getPaths()) {
            services.addAll(guessServiceName(path));
        }
        if (this.applicationEventPublisher != null) {
            for (String service : services) {
                LOGGER.info("Refresh for: " + service);
                this.applicationEventPublisher
                        .publishEvent(new RefreshRemoteApplicationEvent(this,
                                this.contextId, service));
            }
        }

    }

    private void gitPullLocalBranches(Git git, CredentialsProvider credProvider, List<Ref> branches)
            throws GitAPIException, IOException{
        for(Ref branch : branches){
            // checkout each branch then reset checkout to master
            git.checkout().setName(branch.getName()).call();
            git.pull().setCredentialsProvider(credProvider).call();

        }
        git.checkout().setName("refs/heads/master").call();
    }

}
