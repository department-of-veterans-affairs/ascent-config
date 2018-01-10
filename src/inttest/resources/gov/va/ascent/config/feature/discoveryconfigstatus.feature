Feature: Log in to discovery service to check the config service is up
@discoveryconfigstatus
   Scenario: Log in to discovery service to check the config status
      Given I pass the header information for discovery service
      | Pragma       | no-cache        |
      When user makes a request to DiscoveryURL
      Then verify config service is registered 
      And the response code is 200

      
     