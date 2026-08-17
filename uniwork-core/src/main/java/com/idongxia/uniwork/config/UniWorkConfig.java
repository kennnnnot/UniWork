package com.idongxia.uniwork.config;

import com.idongxia.uniwork.UniWorkException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only flattened configuration used by channel providers.
 */
public final class UniWorkConfig {

    private final Map<String, String> values;

    private UniWorkConfig(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(
                new LinkedHashMap<String, String>(values));
    }

    public static UniWorkConfig of(Map<String, String> values) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        return new UniWorkConfig(values);
    }

    public String get(String key) {
        return values.get(normalize(key));
    }

    public String get(String key, String defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : value;
    }

    public String required(String key) {
        String value = get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new UniWorkException("缺少 UniWork 配置项：" + normalize(key));
        }
        return value;
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new UniWorkException("UniWork 配置项不是有效整数：" + normalize(key), e);
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    public boolean hasPrefix(String prefix) {
        String normalized = normalize(prefix);
        for (String key : values.keySet()) {
            if (key.equals(normalized) || key.startsWith(normalized + ".")) {
                return true;
            }
        }
        return false;
    }

    public UniWorkConfig section(String prefix) {
        String normalized = normalize(prefix);
        String nestedPrefix = normalized + ".";
        Map<String, String> section = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().startsWith(nestedPrefix)) {
                section.put(entry.getKey().substring(nestedPrefix.length()), entry.getValue());
            }
        }
        return new UniWorkConfig(section);
    }

    public Map<String, String> asMap() {
        return values;
    }

    private static String normalize(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("configuration key must not be blank");
        }
        String normalized = key.trim();
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
