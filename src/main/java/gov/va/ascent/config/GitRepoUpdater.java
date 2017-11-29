package gov.va.ascent.config;


import java.io.IOException;

@FunctionalInterface
public interface GitRepoUpdater {

    void updateRepo() throws IOException;

}
