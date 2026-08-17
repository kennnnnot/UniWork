package com.idongxia.uniwork.support;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * 平台适配器共用的 URL 拼接与编码工具。
 * URL joining and encoding helpers shared by platform adapters.
 */
public final class UrlSupport {

    private UrlSupport() {
    }

    /** 连接基础地址和绝对路径。Joins a base URL and an absolute path. */
    public static String join(String baseUrl, String absolutePath) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        if (absolutePath == null || absolutePath.trim().isEmpty()) {
            throw new IllegalArgumentException("absolutePath must not be blank");
        }
        String base = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        String path = absolutePath.startsWith("/") ? absolutePath : "/" + absolutePath;
        return base + path;
    }

    /**
     * 追加已经成对排列的查询参数，名称和值都会编码。
     * Appends paired query parameters, encoding both names and values.
     */
    public static String query(String baseUrl, String... namesAndValues) {
        if (namesAndValues == null || namesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("query parameters must be name-value pairs");
        }
        StringBuilder result = new StringBuilder(baseUrl);
        char separator = baseUrl.indexOf('?') >= 0 ? '&' : '?';
        for (int i = 0; i < namesAndValues.length; i += 2) {
            String value = namesAndValues[i + 1];
            if (value == null) {
                continue;
            }
            result.append(separator)
                    .append(encode(namesAndValues[i]))
                    .append('=')
                    .append(encode(value));
            separator = '&';
        }
        return result.toString();
    }

    /** 按 RFC 3986 规则编码 URL 组件。Encodes a URL component using RFC 3986 conventions. */
    public static String encode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        try {
            return URLEncoder.encode(value, "UTF-8")
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is required by Java", e);
        }
    }
}
