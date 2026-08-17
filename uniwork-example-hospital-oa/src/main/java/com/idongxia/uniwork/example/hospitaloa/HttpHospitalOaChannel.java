package com.idongxia.uniwork.example.hospitaloa;

import com.idongxia.uniwork.SendResult;
import com.idongxia.uniwork.UniWorkException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 示例协议：向医院自有 OA 地址 POST JSON 消息；真实项目可以替换请求头和报文，而不改变业务调用。
 * Example protocol that posts JSON to a hospital OA endpoint; real adapters can replace headers and payloads.
 */
final class HttpHospitalOaChannel implements HospitalOaChannel {

    private final String endpoint;
    private final String appId;
    private final String secret;
    private final String defaultTitle;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    HttpHospitalOaChannel(
            String endpoint,
            String appId,
            String secret,
            String defaultTitle,
            int connectTimeoutMillis,
            int readTimeoutMillis) {
        this.endpoint = requireText(endpoint, "endpoint");
        this.appId = requireText(appId, "appId");
        this.secret = requireText(secret, "secret");
        this.defaultTitle = requireText(defaultTitle, "defaultTitle");
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    @Override
    public SendResult sendContent(String receiver, String content) {
        return sendContent(receiver, defaultTitle, content);
    }

    @Override
    public SendResult sendContent(String receiver, String title, String content) {
        requireText(receiver, "receiver");
        requireText(title, "title");
        requireText(content, "content");

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-App-Id", appId);
            connection.setRequestProperty("X-App-Secret", secret);

            byte[] body = json(receiver, title, content)
                    .getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            OutputStream output = connection.getOutputStream();
            try {
                output.write(body);
            } finally {
                output.close();
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new UniWorkException(
                        "医院 OA 消息发送失败，HTTP 状态码 " + status
                                + formatResponseBody(connection.getErrorStream()));
            }
            return SendResult.accepted(
                    "hospital-oa",
                    connection.getHeaderField("X-Message-Id"),
                    connection.getHeaderField("X-Request-Id"));
        } catch (IOException e) {
            throw new UniWorkException("医院 OA 消息发送失败：" + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String json(String receiver, String title, String content) {
        return "{"
                + "\"receiver\":\"" + escapeJson(receiver) + "\","
                + "\"title\":\"" + escapeJson(title) + "\","
                + "\"content\":\"" + escapeJson(content) + "\""
                + "}";
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
            }
        }
        return escaped.toString();
    }

    private static String formatResponseBody(InputStream input) {
        if (input == null) {
            return "";
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[512];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1 && total < 2048) {
                int accepted = Math.min(read, 2048 - total);
                output.write(buffer, 0, accepted);
                total += accepted;
            }
            String body = new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
            return body.isEmpty() ? "" : "，响应：" + body;
        } catch (IOException ignored) {
            return "";
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
                // 格式化原始错误时无法再安全处理关闭失败。Nothing useful remains to do while formatting an error.
            }
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
