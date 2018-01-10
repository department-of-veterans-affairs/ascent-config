Feature: Log in to config service to check the service is up
@configstatus
   Scenario Outline: Log in to config service to check the status 
      Given I pass the header information for config service
      | Pragma       | no-cache        |
      When user makes a request to config "<ServiceURL>"
      Then the response code must be for config service 200

Examples: 
      | ServiceURL          |
      |/actuator/health     |