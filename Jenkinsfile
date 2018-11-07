@Library('ascent') _

microservicePipeline {
    imageName = 'ascent/ascent-config'

    /*
    Define a mapping of environment variables that will be populated with Vault token values
    from the associated vault token role
    */
    vaultTokens = [
        "VAULT_TOKEN": "ascent-platform"
    ]
    testEnvironment = ['docker-compose.yml', 'docker-compose.override.yml']
    serviceToTest = 'ascent-config'
    deployWaitTime = 90
    testVaultTokenRole = "ascent-platform"
    containerPort = 8760

    /*********  Deployment Configuration ***********/
    stackName = "config"
    serviceName = "ascent-config"

    //Default Deployment Configuration Values
    //composeFiles = ["docker-compose.yml"]
}