# README #

This document provides the details of **Ascent Config Acceptance test** .

## Acceptance test for Ascent Config ##
Acceptance test are created to make sure the core services in ascent config are working as expected.

Project uses Java - Maven platform, the REST-Assured jars for core API validations.

## Project Structure ##

src/inttest/gov/va/ascent/features - This is where you will create the cucumber feature files that contain the Feature and Scenarios for the config service you are testing.

src/inttest/java/gov/va/ascent/config/steps- The implementation steps related to the feature and scenarios mentioned in the cucumber file for the API needs to be created in this location.

src/inttest/java/gov/va/ascent/config/runner -Cucumber runner class that contains all feature file entries that needs to be executed at runtime. The annotations provided in the cucumber runner class will assist in bridging the features to step definitions.

src/inttest/resources/logback-test.xml - Logback Console Appender pattern and loggers defined for this project

src/inttest/resources/config/vetsservices-ci.properties – CI configuration properties such as URL are specified here.

src/inttest/resources/config/vetservices-stage.properties – STAGE configuration properties such as URL are specified here.

## Execution ##

**Command Line:** Use this command(s) to execute the config acceptance test. 

Default Local: mvn verify –Pinttest

Note: By default, mvn verify –Pinttest executes the test in headless browser

mvn verify -Pinttest -Dbrowser=BrowserName

Here BrowserName  can be “HtmlUnit” or “CHROME”

If you want to execute the test in chrome browser. Use this below command. 

mvn clean verify -Pinttest -Dbrowser=CHROME -DwebdriverPath=”Path of the chrome driver”

Use below sample commands to execute for different environment:

CI: mvn -Ddockerfile.skip=true integration-test -Pinttest -Dbrowser=HtmlUnit -Dtest.env=ci -DX-Vault-Token=<> -DX-Config-Token=<> -DbaseURL=https://ci.internal.vetservices.gov:8760

CI: mvn -Ddockerfile.skip=true integration-test -Pinttest -Dbrowser=HtmlUnit -DX-Vault-Token=<> -DX-Config-Token=<> -DbaseURL=https://ci.internal.vetservices.gov:8760 -Dvault.url=https://vault.internal.vetservices.gov:8200/v1/secret/application

STAGE : mvn -Ddockerfile.skip=true integration-test -Pinttest -Dbrowser=HtmlUnit -Dtest.env=ci -DX-Vault-Token=<> -DX-Config-Token=<> -DbaseURL=https://stage.internal.vetservices.gov:8760

STAGE: mvn -Ddockerfile.skip=true integration-test -Pinttest -Dbrowser=HtmlUnit -DX-Vault-Token=<> -DX-Config-Token=<> -DbaseURL=https://stage.internal.vetservices.gov:8760 -Dvault.url=https://vault.internal.vetservices.gov:8200/v1/secret/application

The parameter X-Vault-Token is not valid for local environment. It is passed thru pipeline. 
