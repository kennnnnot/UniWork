package com.idongxia.uniwork;

import com.idongxia.uniwork.config.UniWorkConfig;
import com.idongxia.uniwork.config.UniWorkConfigLoader;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniWorkConfigTest {

    @Test
    void scopesChannelConfiguration() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("hospital-oa.endpoint", "https://oa.example.com");
        values.put("hospital-oa.timeout", "3000");

        UniWorkConfig config = UniWorkConfig.of(values);
        UniWorkConfig section = config.section("hospital-oa");

        assertTrue(config.hasPrefix("hospital-oa"));
        assertEquals("https://oa.example.com", section.required("endpoint"));
        assertEquals(3000, section.getInt("timeout", 1000));
    }

    @Test
    void loadsPropertiesForLegacyJavaApplications() {
        UniWorkConfig config = UniWorkConfigLoader.load(
                "uniwork.properties",
                Thread.currentThread().getContextClassLoader());

        assertEquals("properties", config.required("testing.prefix"));
    }

    @Test
    void reportsMissingRequiredConfigurationWithOneExceptionType() {
        UniWorkConfig config = UniWorkConfig.of(
                new LinkedHashMap<String, String>());

        UniWorkException exception = assertThrows(
                UniWorkException.class,
                () -> config.required("endpoint"));

        assertEquals("缺少 UniWork 配置项：endpoint", exception.getMessage());
    }
}
