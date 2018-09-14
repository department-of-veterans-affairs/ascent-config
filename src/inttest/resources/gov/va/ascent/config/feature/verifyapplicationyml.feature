Feature: Navigate to application YML and assert the property of application yml
@verifyapplicationyml
  Scenario Outline: Navigate to application YML and assert the property of application yml
  
      Given I pass the header information for applicationyml 
      | Pragma       | no-cache        |
      When user makes a request to "<ServiceURL>"
      Then the response code must be for application yml 200
      And assert the "<propertyname>" and value should be "<value>"
    
  Examples: 
      | ServiceURL          | propertyname | value| 
      |/application/aws-dev/development/application.yml|ascent.acceptance.testing.property | sampleProperty |
     