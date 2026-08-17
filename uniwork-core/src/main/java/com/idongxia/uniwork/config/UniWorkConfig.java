package com.idongxia.uniwork.config;

import com.idongxia.uniwork.UniWorkException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 渠道 Provider 使用的只读扁平化配置，例如 {@code wecom.corp-id}。
 * Read-only flattened configuration used by channel providers, for example {@code wecom.corp-id}.
 */
public final class UniWorkConfig {

    private final Map<String, String> values;

    private UniWorkConfig(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(
                new LinkedHashMap<String, String>(values));
    }

    /** 从键值集合创建只读配置。Creates read-only configuration from key-value pairs. */
    public static UniWorkConfig of(Map<String, String> values) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        return new UniWorkConfig(values);
    }

    /** 返回配置值，不存在时为 {@code null}。Returns a value, or {@code null} when absent. */
    public String get(String key) {
        return values.get(normalize(key));
    }

    /** 返回配置值，不存在时使用默认值。Returns a value or the supplied default. */
    public String get(String key, String defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : value;
    }

    /** 返回必填配置，缺失时抛出统一异常。Returns a required value or throws the unified exception. */
    public String required(String key) {
        String value = get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new UniWorkException("缺少 UniWork 配置项：" + normalize(key));
        }
        return value;
    }

    /** 返回整数配置。Returns an integer configuration value. */
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

    /** 返回布尔配置。Returns a boolean configuration value. */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    /** 判断指定配置段是否存在。Checks whether a configuration section exists. */
    public boolean hasPrefix(String prefix) {
        String normalized = normalize(prefix);
        for (String key : values.keySet()) {
            if (key.equals(normalized) || key.startsWith(normalized + ".")) {
                return true;
            }
        }
        return false;
    }

    /** 提取指定前缀下的子配置并移除该前缀。Extracts a section and removes its prefix. */
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

    /** 返回不可修改的扁平化键值集合。Returns the unmodifiable flattened values. */
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
