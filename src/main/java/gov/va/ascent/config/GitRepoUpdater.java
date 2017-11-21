package gov.va.ascent.config;


import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;

public interface GitRepoUpdater {

    void updateRepo() throws IOException, GitAPIException;

    void updateRepoViaGithub() throws IOException;

    void compareCommits() throws IOException, GitAPIException;

}
