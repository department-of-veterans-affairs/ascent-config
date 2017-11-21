package gov.va.ascent.config;


import com.netflix.discovery.converters.Auto;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class GitRepoUpdateController {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitRepoUpdateController.class);

    @Autowired
    GitRepoUpdater gitRepoUpdater;


    @RequestMapping("/update")
    public void updateRepo(){
        try {
            gitRepoUpdater.updateRepo();
        } catch(IOException | GitAPIException e){
            LOGGER.error("Error occurred: ", e);
        }
    }

}
