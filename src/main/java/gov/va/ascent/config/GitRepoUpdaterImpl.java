package gov.va.ascent.config;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.cloud.config.monitor.PropertyPathNotification;
import org.springframework.cloud.config.server.environment.JGitEnvironmentRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @author npaulus
 *
 *         The purpose of this class is to periodically check for external configuration changes on Github and publish a
 *         refresh event to the impacted applications running on the platform.
 */
@Component
public class GitRepoUpdaterImpl implements GitRepoUpdater, ApplicationEventPublisherAware, ApplicationContextAware {

	/* The logger. */
	private static final Logger LOGGER = LoggerFactory.getLogger(GitRepoUpdaterImpl.class);

	/* Master branch. */
	private static final String REFS_HEADS_MASTER = "refs/heads/master";

	/* Development branch. */
	private static final String REFS_HEADS_DEVELOPMENT = "refs/heads/development";

	/* Application constant. */
	private static final String APPLICATION = "application";

	/* The application event publisher. */
	private ApplicationEventPublisher applicationEventPublisher;

	/* The context id. */
	private String contextId = UUID.randomUUID().toString();

	/* The base directory for the git repo. */
	@Value("${BASE_DIR:../configrepo}")
	private String gitRepoPath;

	/* The branch to monitor for updates. */
	@Value("${ascent.cloud.config.label:''}")
	private String ascentCloundConfigRepoBranch;

	/* Keep track of latest commit in each local branch. */
	private final Map<String, RevCommit> mostRecentCommitByBranchName = new HashMap<>();

	/* The JGIT Environment repository. */
	@Autowired
	JGitEnvironmentRepository environmentRepository;

	/* The application context id. */
	@Override
	public void setApplicationContext(final ApplicationContext applicationContext) {
		this.contextId = applicationContext.getId();
	}

	/* The application event publisher. */
	@Override
	public void setApplicationEventPublisher(
			final ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	/**
	 *
	 * Updates local repo, then guesses at the appropriate apps that have external configuration updates
	 * to send a notification to refresh their application contexts.
	 *
	 * Scheduled to run every 5 minutes.
	 *
	 * @throws IOException
	 */
	@Override
	@Scheduled(cron = "0 0/5 * * * *")
	public void updateRepo() throws IOException {

		final CredentialsProvider credProvider = environmentRepository.getGitCredentialsProvider();

		final FileRepositoryBuilder builder = new FileRepositoryBuilder();
		try {
			final Repository repository = builder.setGitDir(new File(gitRepoPath + "/.git"))
					.setMustExist(true)
					.readEnvironment()
					.build();
			final Git git = new Git(repository);
			final List<Ref> branches = Collections.unmodifiableList(git.branchList().call());
			LOGGER.debug("Got the repo opened: " + repository.getFullBranch());

			if (mostRecentCommitByBranchName.isEmpty()) {
				firstCheckForUpdates(git, credProvider, repository);
			} else if (mostRecentCommitByBranchName.size() < branches.size()) {
				checkForNewLocalBranchCheckouts(git, repository);
			}
			gitPullLocalBranches(git, credProvider, branches);

			final Set<String> paths = checkForUpdates(repository, git);

			notifyAppsOfExternalConfigChanges(paths);
		} catch (final GitAPIException e) {
			LOGGER.error("Git Exception occurred: ", e);
		}
	}

	private Set<String> guessServiceName(final String path) {
		final Set<String> services = new LinkedHashSet<>();
		if (path != null) {
			final String stem = StringUtils
					.stripFilenameExtension(StringUtils.getFilename(StringUtils.cleanPath(path)));
			int index = stem.indexOf('-');
			while (index >= 0) {
				final String name = stem.substring(0, index);
				final String profile = stem.substring(index + 1);
				if (APPLICATION.equals(name)) {
					services.add("*:" + profile);
				} else if (!name.startsWith(APPLICATION)) {
					services.add(name + ":" + profile);
				}
				index = stem.indexOf('-', index + 1);
			}
			final String name = stem;
			if (APPLICATION.equals(name)) {
				services.add("*");
			} else if (!name.startsWith(APPLICATION)) {
				services.add(name);
			}
		}
		return services;
	}

	private void firstCheckForUpdates(final Git git, final CredentialsProvider credProvider, final Repository repository)
			throws IOException, GitAPIException {

		final List<Ref> branches = Collections.unmodifiableList(git.branchList().call());
		for (final Ref branch : branches) {
			git.checkout().setName(branch.getName()).call();
			git.pull().setCredentialsProvider(credProvider).setStrategy(MergeStrategy.THEIRS).call();
			final Iterable<RevCommit> commits = git.log().add(repository.resolve(Constants.HEAD)).setMaxCount(1).call();
			LOGGER.debug("Branch Name: " + branch.getName());
			for (final RevCommit newestCommit : commits) {
				LOGGER.debug("Commit sha: " + newestCommit.toObjectId());
				mostRecentCommitByBranchName.put(branch.getName(), newestCommit);
			}

		}
	}

	private void checkForNewLocalBranchCheckouts(final Git git, final Repository repository)
			throws IOException, GitAPIException {
		final List<Ref> branches = Collections.unmodifiableList(git.branchList().call());
		for (final Ref branch : branches) {
			if (!mostRecentCommitByBranchName.containsKey(branch.getName())) {
				LOGGER.debug("New Local Branch found: " + branch.getName());
				git.checkout().setName(branch.getName()).call();
				final Iterable<RevCommit> commits = git.log().add(repository.resolve(Constants.HEAD)).setMaxCount(1).call();
				for (final RevCommit newestCommit : commits) {
					mostRecentCommitByBranchName.put(branch.getName(), newestCommit);
				}
			}
		}
	}

	private Set<String> checkForUpdates(final Repository repository, final Git git) throws IOException, GitAPIException {
		final Set<String> paths = new LinkedHashSet<>();

		final List<Ref> branches = Collections.unmodifiableList(git.branchList().call());

		if ("master".equals(ascentCloundConfigRepoBranch)) {
			git.checkout().setName(REFS_HEADS_MASTER).call();
			final Iterable<RevCommit> commits = git.log().addRange(mostRecentCommitByBranchName.get(REFS_HEADS_MASTER),
					repository.resolve(Constants.HEAD)).call();
			paths.addAll(processUpdatedCommits(commits, REFS_HEADS_MASTER, repository));
		} else if ("development".equals(ascentCloundConfigRepoBranch)) {
			git.checkout().setName(REFS_HEADS_DEVELOPMENT).call();
			final Iterable<RevCommit> commits = git.log().addRange(mostRecentCommitByBranchName.get(REFS_HEADS_DEVELOPMENT),
					repository.resolve(Constants.HEAD)).call();
			paths.addAll(processUpdatedCommits(commits, REFS_HEADS_DEVELOPMENT, repository));
		} else {
			for (final Ref branch : branches) {
				LOGGER.debug("Branch ID: " + branch.getName());
				git.checkout().setName(branch.getName()).call();
				final Iterable<RevCommit> commits = git.log().addRange(mostRecentCommitByBranchName.get(branch.getName()),
						repository.resolve(Constants.HEAD)).call();
				paths.addAll(processUpdatedCommits(commits, branch.getName(), repository));
			}
		}
		return paths;
	}

	private Set<String> processUpdatedCommits(final Iterable<RevCommit> commits, final String branch,
			final Repository repository) throws IOException {
		final Set<String> paths = new LinkedHashSet<>();
		final DiffFormatter diffFmt = new DiffFormatter(DisabledOutputStream.INSTANCE);
		try {
			diffFmt.setRepository(repository);
			int count = 0;
			for (final RevCommit commit : commits) {
				if (count == 0) {
					mostRecentCommitByBranchName.put(branch, commit);
				}
				LOGGER.debug("Commit ID: " + commit.toObjectId());
				final RevTree a = commit.getParentCount() > 0 ? commit.getParent(0).getTree() : null;
				final RevTree b = commit.getTree();

				for (final DiffEntry diff : diffFmt.scan(a, b)) {
					LOGGER.info(diff.getNewPath() + " - " + commit.getId());
					paths.add(diff.getNewPath());
				}
				count++;
			}
		} finally {
			diffFmt.release();
		}
		return paths;
	}

	private void notifyAppsOfExternalConfigChanges(final Set<String> paths) {
		final PropertyPathNotification propertyPathNotification = new PropertyPathNotification();

		if (!paths.isEmpty()) {
			propertyPathNotification.setPaths(paths.toArray(new String[0]));
		} else {
			propertyPathNotification.setPaths(new String[0]);
		}

		final Set<String> services = new LinkedHashSet<>();

		for (final String path : propertyPathNotification.getPaths()) {
			services.addAll(guessServiceName(path));
		}
		if (this.applicationEventPublisher != null) {
			for (final String service : services) {
				LOGGER.info("Refresh for: " + service);
				this.applicationEventPublisher
						.publishEvent(new RefreshRemoteApplicationEvent(this,
								this.contextId, service));
			}
		}

	}

	private void gitPullLocalBranches(final Git git, final CredentialsProvider credProvider, final List<Ref> branches)
			throws GitAPIException {
		for (final Ref branch : branches) {
			git.checkout().setName(branch.getName()).call();
			git.pull().setCredentialsProvider(credProvider).setStrategy(MergeStrategy.THEIRS).call();
		}
	}

}
