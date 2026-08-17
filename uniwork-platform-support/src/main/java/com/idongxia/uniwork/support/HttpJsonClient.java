package com.idongxia.uniwork.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.idongxia.uniwork.UniWorkException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * 基于 JDK 8 {@link HttpURLConnection} 的小型 JSON 客户端。
 * Small JSON client based on the JDK 8 {@link HttpURLConnection} API.
 *
 * <p>异常信息不会回显完整请求 URL，避免 query 中的令牌或密钥进入日志。</p>
 * <p>Exception messages intentionally omit full request URLs so query credentials do not leak into logs.</p>
 */
public final class HttpJsonClient {

    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    /**
     * 创建带连接和读取超时的客户端。
     * Creates a client with connect and read timeouts.
     */
    public HttpJsonClient(int connectTimeoutMillis, int readTimeoutMillis) {
        if (connectTimeoutMillis <= 0 || readTimeoutMillis <= 0) {
            throw new IllegalArgumentException("HTTP timeout must be greater than zero");
        }
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    /** 发送 GET 请求。Executes a GET request. */
    public HttpJsonResponse get(String url) {
        return get(url, Collections.<String, String>emptyMap());
    }

    /** 发送带请求头的 GET 请求。Executes a GET request with headers. */
    public HttpJsonResponse get(String url, Map<String, String> headers) {
        return execute("GET", url, headers, null);
    }

    /** 发送 JSON POST 请求。Executes a JSON POST request. */
    public HttpJsonResponse post(String url, JsonNode body) {
        return post(url, Collections.<String, String>emptyMap(), body);
    }

    /** 发送带请求头的 JSON POST 请求。Executes a JSON POST request with headers. */
    public HttpJsonResponse post(String url, Map<String, String> headers, JsonNode body) {
        if (body == null) {
            throw new IllegalArgumentException("body must not be null");
        }
        return execute("POST", url, headers, JsonSupport.write(body));
    }

    private HttpJsonResponse execute(
            String method,
            String url,
            Map<String, String> headers,
            String requestBody) {
        HttpURLConnection connection = null;
        try {
            connection = open(url);
            connection.setRequestMethod(method);
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setRequestProperty("Accept", "application/json");
            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }
            if (requestBody != null) {
                byte[] bytes = requestBody.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                write(connection, bytes);
            }

            int status = connection.getResponseCode();
            String responseText = read(status >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream());
            JsonNode responseBody;
            if (responseText.isEmpty()) {
                responseBody = NullNode.getInstance();
            } else {
                try {
                    responseBody = JsonSupport.read(responseText);
                } catch (UniWorkException e) {
                    if (status >= 200 && status < 300) {
                        throw e;
                    }
                    // 网关错误页可能不是 JSON；保留原文，让上层仍能报告真实 HTTP 状态。
                    // Gateway error pages may be non-JSON; preserve text so callers can report HTTP status.
                    responseBody = TextNode.valueOf(responseText);
                }
            }
            return new HttpJsonResponse(status, responseBody, responseText);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new UniWorkException("调用第三方平台 HTTP 接口失败：" + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        Object connection = URI.create(url).toURL().openConnection();
        if (!(connection instanceof HttpURLConnection)) {
            throw new IllegalArgumentException("only HTTP and HTTPS URLs are supported");
        }
        return (HttpURLConnection) connection;
    }

    private static void write(HttpURLConnection connection, byte[] bytes) throws IOException {
        OutputStream output = connection.getOutputStream();
        try {
            output.write(bytes);
        } finally {
            output.close();
        }
    }

    private static String read(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new UniWorkException("第三方平台响应超过 1 MiB 安全上限");
                }
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }
}
