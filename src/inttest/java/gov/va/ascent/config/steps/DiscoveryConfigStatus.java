package gov.va.ascent.config.steps;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import gov.va.ascent.config.util.AppUtil;
import gov.va.ascent.test.framework.restassured.BaseStepDef;


public class DiscoveryConfigStatus extends BaseStepDef {

	final Logger log = LoggerFactory.getLogger(DiscoveryConfigStatus.class);
	private String discoveryURL;
	@Before({"@discoveryconfigstatus"})
	public void setUpREST() {
		initREST();
		discoveryURL = AppUtil.getDiscoveryURL();
	}

	@Given("^I pass the header information for discovery service$")
	public void passTheHeaderInformationForConfig(Map<String, String> tblHeader) throws Throwable {
		passHeaderInformation(tblHeader);
	}

	@When("user makes a request to DiscoveryURL$")
	public void makerequesustoappsurlGet() throws Throwable {
		invokeAPIUsingGet(discoveryURL, false);
	}
	@Then("^verify config service is registered$")
	public void verifyconfigservericeRefistered(int intStatusCode) throws Throwable {
		
	}
	@And("^the response code is (\\d+)$\"")
	public void serviceresposestatuscodemustbe(int intStatusCode) throws Throwable {
		validateStatusCode(intStatusCode);
	}

	@After({"@discoveryconfigstatus"})
	public void cleanUp(Scenario scenario) {
		postProcess(scenario);
	}

}
