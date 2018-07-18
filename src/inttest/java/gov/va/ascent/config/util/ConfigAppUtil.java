package gov.va.ascent.config.util;

import gov.va.ascent.test.framework.service.RESTConfigService;

public class ConfigAppUtil {
	
	private ConfigAppUtil() {

	}

	public static String getBaseURL() {
		return RESTConfigService.getBaseURL("data.'ascent.security.username'", "data.'ascent.security.password'");
		
	}
	
	
}
