package gov.va.ascent.config.steps;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import gov.va.ascent.config.util.AppUtil;
import gov.va.ascent.test.framework.restassured.BaseStepDef;


public class ConfigStatus extends BaseStepDef {

	final Logger log = LoggerFactory.getLogger(ConfigStatus.class);

	@Before({"@configstatus"})
	public void setUpREST() {
		initREST();
	}

	@Given("^I pass the header information for config service$")
	public void passTheHeaderInformationForConfig(Map<String, String> tblHeader) throws Throwable {
		passHeaderInformation(tblHeader);
	}

	@When("^user makes a request to config \"([^\"]*)\"$")
	public void makerequesustoappsurlGet(String strURL) throws Throwable {
		invokeAPIUsingGet(AppUtil.getBaseURL() + strURL, false);
	}
	@Then("^the response code must be for config service (\\d+)$")
	public void serviceresposestatuscodemustbe(int intStatusCode) throws Throwable {
		validateStatusCode(intStatusCode);
	}


	@After({"@configstatus"})
	public void cleanUp(Scenario scenario) {
		postProcess(scenario);
	}

}
