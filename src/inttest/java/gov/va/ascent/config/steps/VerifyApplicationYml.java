package gov.va.ascent.config.steps;

import static org.junit.Assert.assertEquals;

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
import gov.va.ascent.config.util.ConfigAppUtil;
import gov.va.ascent.test.framework.restassured.BaseStepDef;
import gov.va.ascent.test.framework.service.YamlReader;

public class VerifyApplicationYml extends BaseStepDef {

	final Logger log = LoggerFactory.getLogger(VerifyApplicationYml.class);

	@Before({"@verifyapplicationyml"})
	public void setUpREST() {
		initREST();
	}

	@Given("^I pass the header information for applicationyml$")
	public void passTheHeaderInformationForConfig(Map<String, String> tblHeader) throws Throwable {
		passHeaderInformation(tblHeader);
	}

	@When("^user makes a request to \"([^\"]*)\"$")
	public void makerequesustoappsurlGet(String strURL) throws Throwable { 
		String configToken = System.getProperty("X-Config-Token");
		headerMap.put("X-Config-Token", configToken);
		invokeAPIUsingGet(ConfigAppUtil.getBaseURL() + strURL, false);
	}
	@Then("^the response code must be for application yml (\\d+)$")
	public void serviceresposestatuscodemustbe(int intStatusCode) throws Throwable {
		validateStatusCode(intStatusCode);
	}

	@And("^assert the \"([^\"]*)\" and value should be \"([^\"]*)\"$")
	public void checkProperty(String propertyName, String propertyValue) throws Throwable {
		String value = YamlReader.getProperty(strResponse, propertyName);
		assertEquals(value, propertyValue);
	}
	
	@After({"@verifyapplicationyml"})
	public void cleanUp(Scenario scenario) {
		postProcess(scenario);
	}

}
