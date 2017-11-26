package gov.va.ascent.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.config.server.environment.JGitEnvironmentRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(SpringRunner.class)
public class GitRepoUpdaterImplTest {

    @SuppressWarnings("rawtypes")
    @Mock
    private Appender mockAppender;

    @Captor
    private ArgumentCaptor<LoggingEvent> captorLoggingEvent;

    @Mock
    JGitEnvironmentRepository environmentRepository;

    @InjectMocks
    private GitRepoUpdaterImpl gitRepoUpdater = new GitRepoUpdaterImpl();

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    private static Git remoteGit;

    private static Git localGit;

    private static RevCommit writeFileAndCommit(String path, String filename, String content, String commitMessage)
            throws IOException, GitAPIException {
        Files.write(Paths.get(path + "/" + filename), content.getBytes());
        remoteGit.add().addFilepattern(filename).call();
        return remoteGit.commit().setMessage(commitMessage).call();
    }

    @Before
    public void setup() throws Exception {
        //Set up git repositories
        remoteGit = Git.init().setDirectory(new File("target/remote-repo")).call();

        String content = "Hello World !!";
        String content2 = "Nate: Hello World !!";

        writeFileAndCommit("target/remote-repo", "Test1.txt",
                content, "First Commit");
        writeFileAndCommit("target/remote-repo", "Test1.txt",
                content2, "Second Commit");

        localGit = Git.cloneRepository().setURI("../remote-repo")
                .setDirectory(new File("target/local-repo")).call();

        //set up logger for testing
        final Logger logger = (Logger) LoggerFactory.getLogger(GitRepoUpdaterImpl.class);
        logger.addAppender(mockAppender);
        logger.setLevel(Level.INFO);

        //Set up class under test
        ReflectionTestUtils.setField(gitRepoUpdater,"gitRepoPath","target/local-repo");
        gitRepoUpdater.setApplicationContext(applicationContext);
        gitRepoUpdater.setApplicationEventPublisher(applicationEventPublisher);

    }

    @SuppressWarnings("unchecked")
    @After
    public void teardown() {
        final Logger logger = (Logger) LoggerFactory.getLogger(GitRepoUpdater.class);
        logger.detachAppender(mockAppender);
        FileSystemUtils.deleteRecursively(new File("target/remote-repo"));
        FileSystemUtils.deleteRecursively(new File("target/local-repo"));
        Map<String, RevCommit> cleanMap =
                (Map<String, RevCommit>)
                        ReflectionTestUtils.getField(gitRepoUpdater, "mostRecentCommitByBranchName");
        cleanMap.clear();
    }

    @Test
    public void testConfigChange() throws Exception{
        // Setup
        setupMostRecentCommitsMap();

        // initiate new commit to show update
        String content = "Hello World !! 3";
        writeFileAndCommit("target/remote-repo","Test1.txt", content, "Newer Commit");
        gitRepoUpdater.updateRepo();

        // verify
        verify(mockAppender, times(2)).doAppend(captorLoggingEvent.capture());
        final List<LoggingEvent> loggingEvents = captorLoggingEvent.getAllValues();
        assertThat(loggingEvents.get(1).getMessage(), is("Refresh for: Test1"));
    }

    @Test
    public void testNewBranchChange() throws Exception{
        //Setup
        final Logger logger = (Logger) LoggerFactory.getLogger(GitRepoUpdaterImpl.class);
        logger.addAppender(mockAppender);
        logger.setLevel(Level.DEBUG);
        setupMostRecentCommitsMap();

        // create new remote branch and pull it to local one
        remoteGit.branchCreate().setName("feature").setForce(true).call();
        remoteGit.checkout().setName("feature").call();
        writeFileAndCommit("target/local-repo","Test1.txt", "Completely different", "Commit in new branch");
        localGit.fetch().call();
        localGit.checkout()
                .setCreateBranch(true)
                .setName("feature")
                .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                .setStartPoint("origin/" + "feature")
                .setForce(true)
                .call();

        gitRepoUpdater.updateRepo();

        // verify
        Map<String, RevCommit> result = (Map<String, RevCommit>) ReflectionTestUtils.getField(gitRepoUpdater,"mostRecentCommitByBranchName");
        assertThat(result.size(), is(2));
        verify(mockAppender, times(4)).doAppend(captorLoggingEvent.capture());
        final List<LoggingEvent> loggingEvents = captorLoggingEvent.getAllValues();

        assertThat(loggingEvents.get(1).getMessage(), is("New Local Branch found: refs/heads/feature"));
    }

    @Test
    public void testFirstRun() throws Exception {
        final Logger logger = (Logger) LoggerFactory.getLogger(GitRepoUpdaterImpl.class);
        logger.addAppender(mockAppender);
        logger.setLevel(Level.DEBUG);

        gitRepoUpdater.updateRepo();

        Map<String, RevCommit> result = (Map<String, RevCommit>)
                ReflectionTestUtils.getField(gitRepoUpdater,"mostRecentCommitByBranchName");
        assertThat(result.size(), is(1));
        verify(mockAppender, times(4)).doAppend(captorLoggingEvent.capture());
        final List<LoggingEvent> loggingEvents = captorLoggingEvent.getAllValues();

        assertTrue(loggingEvents.get(0).getMessage().equals("Got the repo opened: refs/heads/master"));
        assertTrue(loggingEvents.get(1).getMessage().equals("Branch Name: refs/heads/master"));
        assertTrue(loggingEvents.get(2).getMessage().contains("Commit sha: "));
        assertTrue(loggingEvents.get(3).getMessage().equals("Branch ID: refs/heads/master"));

    }

    @Test
    public void testMasterConfigChange() throws Exception{
        // Setup
        writeFileAndCommit("target/remote-repo","application.yml",
                "Config 1", "Config Commit");
        writeFileAndCommit("target/remote-repo","foo-bar-application.yml",
                "Config 2", "More config Commit");
        ReflectionTestUtils.setField(gitRepoUpdater, "ascentCloundConfigRepoBranch", "master");

        setupMostRecentCommitsMap();

        // initiate new commit to show update
        writeFileAndCommit("target/remote-repo","application.yml",
                "Config 3", "Config Commit");
        writeFileAndCommit("target/remote-repo","foo-bar-application.yml",
                "Config 4", "More config Commit");

        gitRepoUpdater.updateRepo();

        // verify
        verify(mockAppender, times(6)).doAppend(captorLoggingEvent.capture());
        final List<LoggingEvent> loggingEvents = captorLoggingEvent.getAllValues();

        assertTrue(loggingEvents.get(0).getMessage().contains("foo-bar-application.yml"));
        assertTrue(loggingEvents.get(1).getMessage().contains("application.yml"));
        assertTrue(loggingEvents.get(2).getMessage().equals("Refresh for: foo:bar-application"));
        assertTrue(loggingEvents.get(3).getMessage().equals("Refresh for: foo-bar:application"));
        assertTrue(loggingEvents.get(4).getMessage().equals("Refresh for: foo-bar-application"));
        assertTrue(loggingEvents.get(5).getMessage().equals("Refresh for: *"));
    }

    @Test
    public void testDevelopmentAndMasterConfigChange() throws Exception{
        // Setup
        writeFileAndCommit("target/remote-repo","bar-foo-app.yml",
                "Config 1", "Config Commit");

        remoteGit.branchCreate().setName("development").setForce(true).call();
        remoteGit.checkout().setName("development").call();

        writeFileAndCommit("target/remote-repo","application-foo-bar.yml",
                "Config 2", "More config Commit");
        ReflectionTestUtils.setField(gitRepoUpdater, "ascentCloundConfigRepoBranch", "development");
        localGit.fetch().call();
        localGit.checkout()
                .setCreateBranch(true)
                .setName("development")
                .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                .setStartPoint("origin/" + "development")
                .setForce(true)
                .call();
        setupMostRecentCommitsMap();

        // initiate new commit to show update
        remoteGit.checkout().setName("master").call();
        writeFileAndCommit("target/remote-repo","bar-foo-app.yml", "Config 3", "Config Commit");
        remoteGit.checkout().setName("development").call();
        writeFileAndCommit("target/remote-repo","application-foo-bar.yml", "Config 4", "More config Commit");

        gitRepoUpdater.updateRepo();

        // verify
        verify(mockAppender, times(2)).doAppend(captorLoggingEvent.capture());
        final List<LoggingEvent> loggingEvents = captorLoggingEvent.getAllValues();

        assertTrue(loggingEvents.get(0).getMessage().contains("application-foo-bar.yml"));
        assertTrue(loggingEvents.get(1).getMessage().equals("Refresh for: *:foo-bar"));
    }

    private void setupMostRecentCommitsMap() throws GitAPIException, IOException {
        Map<String, RevCommit> mostRecentCommits = new LinkedHashMap<>();
        Repository repository = localGit.getRepository();
        final List<Ref > branches = Collections.unmodifiableList(localGit.branchList().call());
        for(Ref branch: branches){
            localGit.checkout().setName(branch.getName()).call();
            localGit.pull().setStrategy(MergeStrategy.THEIRS).call();
            Iterable<RevCommit> commits = localGit.log().add(repository.resolve(Constants.HEAD)).setMaxCount(1).call();
            for(RevCommit newestCommit: commits){
                mostRecentCommits.put(branch.getName(), newestCommit);
            }
        }
        ReflectionTestUtils.setField(gitRepoUpdater, "mostRecentCommitByBranchName", mostRecentCommits);
    }
}
