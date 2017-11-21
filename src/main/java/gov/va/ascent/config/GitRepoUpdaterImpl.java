package gov.va.ascent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
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
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
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

    private static final String REFS_HEADS_MASTER = "/refs/heads/master";
    private static final String REFS_HEADS_DEVELOPMENT = "/refs/heads/development";
    private ApplicationEventPublisher applicationEventPublisher;

    private String contextId = UUID.randomUUID().toString();

    @Value("${BASE_DIR:../configrepo}")
    private String gitRepoPath;

    @Value("${ascent.cloud.config.label:''}")
    private String ascentCloundConfigRepoBranch;

    @Autowired
    private Environment environment;

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
    @Scheduled(cron = "0 0/5 * * * *")
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
            git.pull().setCredentialsProvider(credProvider).setStrategy(MergeStrategy.THEIRS).call();
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

        final List<Ref> branches = Collections.unmodifiableList(git.branchList().call());

        if(ascentCloundConfigRepoBranch.equals("master")){
            git.checkout().setName(REFS_HEADS_MASTER).call();
            Iterable<RevCommit> commits = git.log().addRange(mostRecentCommitByBranchName.get(REFS_HEADS_MASTER),
                    repository.resolve(Constants.HEAD)).call();
            processUpdatedCommits(commits, REFS_HEADS_MASTER, repository, paths);
        } else if(ascentCloundConfigRepoBranch.equals("development")){
            git.checkout().setName(REFS_HEADS_DEVELOPMENT).call();
            Iterable<RevCommit> commits = git.log().addRange(mostRecentCommitByBranchName.get(REFS_HEADS_DEVELOPMENT),
                    repository.resolve(Constants.HEAD)).call();
            processUpdatedCommits(commits, REFS_HEADS_DEVELOPMENT, repository, paths);
        } else {
            for (Ref branch : branches) {
                LOGGER.info("Branch ID: " + branch.getName());
                git.checkout().setName(branch.getName()).call();
                Iterable<RevCommit> commits = git.log().addRange(mostRecentCommitByBranchName.get(branch.getName()),
                        repository.resolve(Constants.HEAD)).call();
                processUpdatedCommits(commits, branch.getName(), repository, paths);
            }
        }
        return paths;
    }

    private void processUpdatedCommits(Iterable<RevCommit> commits, String branch, Repository repository, Set<String> paths)
            throws IOException{
        DiffFormatter diffFmt = new DiffFormatter(DisabledOutputStream.INSTANCE);
        diffFmt.setRepository(repository);
        int count = 0;
        for(RevCommit commit : commits){
            if(count == 0){
                mostRecentCommitByBranchName.put(branch, commit);
            }
            LOGGER.info("Commit ID: " + commit.toObjectId());
            final RevTree a = commit.getParentCount() > 0 ? commit.getParent(0).getTree() : null;
            final RevTree b = commit.getTree();

            for(DiffEntry diff: diffFmt.scan(a, b)) {
                LOGGER.info(diff.getNewPath() + " - " + commit.getId());
                paths.add(diff.getNewPath());
            }
            count++;
        }
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
            git.pull().setCredentialsProvider(credProvider).setStrategy(MergeStrategy.THEIRS).call();

        }
        git.checkout().setName("refs/heads/master").call();
    }

}
