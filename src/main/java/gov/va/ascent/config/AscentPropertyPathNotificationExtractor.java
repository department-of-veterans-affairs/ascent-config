package gov.va.ascent.config;

import org.springframework.cloud.config.monitor.PropertyPathNotification;
import org.springframework.cloud.config.monitor.PropertyPathNotificationExtractor;
import org.springframework.util.MultiValueMap;

import java.util.Map;

public class AscentPropertyPathNotificationExtractor implements PropertyPathNotificationExtractor {
    @Override
    public PropertyPathNotification extract(MultiValueMap<String, String> multiValueMap, Map<String, Object> map) {
        return null;
    }
}
