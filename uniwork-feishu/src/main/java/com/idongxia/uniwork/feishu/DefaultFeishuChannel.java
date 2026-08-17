package com.idongxia.uniwork.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.idongxia.uniwork.SendResult;
import com.idongxia.uniwork.UniWorkException;
import com.idongxia.uniwork.UniWorkUser;
import com.idongxia.uniwork.channel.FeishuChannel;
import com.idongxia.uniwork.support.HttpJsonClient;
import com.idongxia.uniwork.support.HttpJsonResponse;
import com.idongxia.uniwork.support.JsonSupport;
import com.idongxia.uniwork.support.UrlSupport;

import java.util.LinkedHashMap;
import java.util.Map;

/** 飞书企业自建应用的默认 Java 8 实现。Default Java 8 implementation for a Feishu custom app. */
final class DefaultFeishuChannel implements FeishuChannel {

    private final String appId;
    private final String appSecret;
    private final String redirectUri;
    private final String oauthScope;
    private final String oauthState;
    private final String receiveIdType;
    private final String userIdType;
    private final String cardButtonText;
    private final String apiBaseUrl;
    private final String accountsBaseUrl;
    private final HttpJsonClient http;
    private volatile AccessToken tenantAccessToken;

    DefaultFeishuChannel(
            String appId,
            String appSecret,
            String redirectUri,
            String oauthScope,
            String oauthState,
            String receiveIdType,
            String userIdType,
            String cardButtonText,
            String apiBaseUrl,
            String accountsBaseUrl,
            int connectTimeoutMillis,
            int readTimeoutMillis) {
        this.appId = requireText(appId, "appId");
        this.appSecret = requireText(appSecret, "appSecret");
        this.redirectUri = redirectUri;
        this.oauthScope = requireText(oauthScope, "oauthScope");
        this.oauthState = requireText(oauthState, "oauthState");
        this.receiveIdType = requireText(receiveIdType, "receiveIdType");
        this.userIdType = requireText(userIdType, "userIdType");
        this.cardButtonText = requireText(cardButtonText, "cardButtonText");
        this.apiBaseUrl = requireText(apiBaseUrl, "apiBaseUrl");
        this.accountsBaseUrl = requireText(accountsBaseUrl, "accountsBaseUrl");
        this.http = new HttpJsonClient(connectTimeoutMillis, readTimeoutMillis);
    }

    @Override
    public SendResult sendContent(String receiver, String content) {
        ObjectNode text = JsonSupport.object();
        text.put("text", requireText(content, "content"));
        return sendMessage(requireText(receiver, "receiver"), "text", text);
    }

    @Override
    public SendResult sendContent(String receiver, String title, String content) {
        return sendContent(
                receiver,
                requireText(title, "title") + "\n" + requireText(content, "content"));
    }

    @Override
    public SendResult sendCard(String receiver, String title, String content, String url) {
        ObjectNode card = JsonSupport.object();
        ObjectNode config = JsonSupport.object();
        config.put("wide_screen_mode", true);
        card.set("config", config);

        ObjectNode header = JsonSupport.object();
        header.put("template", "blue");
        header.set("title", textElement("plain_text", requireText(title, "title")));
        card.set("header", header);

        ArrayNode elements = JsonSupport.array();
        ObjectNode contentElement = JsonSupport.object();
        contentElement.put("tag", "div");
        contentElement.set("text", textElement("lark_md", requireText(content, "content")));
        elements.add(contentElement);

        ObjectNode button = JsonSupport.object();
        button.put("tag", "button");
        button.put("type", "primary");
        button.put("url", requireText(url, "url"));
        button.set("text", textElement("plain_text", cardButtonText));
        ObjectNode action = JsonSupport.object();
        action.put("tag", "action");
        action.set("actions", JsonSupport.array().add(button));
        elements.add(action);
        card.set("elements", elements);
        return sendMessage(requireText(receiver, "receiver"), "interactive", card);
    }

    @Override
    public String loginUrl() {
        return UrlSupport.query(
                UrlSupport.join(accountsBaseUrl, "/open-apis/authen/v1/authorize"),
                "client_id", appId,
                "redirect_uri", requireConfigured(redirectUri, "redirect-uri"),
                "scope", oauthScope,
                "state", oauthState);
    }

    @Override
    public UniWorkUser login(String code) {
        requireText(code, "code");
        ObjectNode request = JsonSupport.object();
        request.put("grant_type", "authorization_code");
        request.put("client_id", appId);
        request.put("client_secret", appSecret);
        request.put("code", code);
        if (redirectUri != null && !redirectUri.trim().isEmpty()) {
            request.put("redirect_uri", redirectUri);
        }
        HttpJsonResponse tokenResponse = http.post(
                UrlSupport.join(apiBaseUrl, "/open-apis/authen/v2/oauth/token"),
                request);
        requireHttpSuccess(tokenResponse, "获取飞书用户 access_token");
        JsonNode tokenBody = tokenResponse.getBody();
        requireOAuthSuccess(tokenBody, "获取飞书用户 access_token");
        String userToken = JsonSupport.firstText(tokenBody, "access_token");
        if (userToken == null) {
            throw new UniWorkException("飞书用户 access_token 响应缺少 access_token");
        }

        HttpJsonResponse userResponse = http.get(
                UrlSupport.join(apiBaseUrl, "/open-apis/authen/v1/user_info"),
                bearerHeaders(userToken));
        requireHttpSuccess(userResponse, "读取飞书登录用户");
        JsonNode body = userResponse.getBody();
        requireApiSuccess(body, "读取飞书登录用户");
        JsonNode user = body.path("data");
        String identity = JsonSupport.firstText(user, "user_id", "open_id", "union_id");
        if (identity == null) {
            throw new UniWorkException("飞书登录结果中没有 user_id、open_id 或 union_id");
        }
        UniWorkUser.Builder builder = UniWorkUser.builder("feishu", identity)
                .name(JsonSupport.firstText(user, "name", "en_name"))
                .avatarUrl(JsonSupport.firstText(user, "avatar_url", "avatar_big"))
                .email(JsonSupport.firstText(user, "email", "enterprise_email"))
                .mobile(JsonSupport.firstText(user, "mobile"));
        addAttribute(builder, user, "openId", "open_id");
        addAttribute(builder, user, "unionId", "union_id");
        addAttribute(builder, user, "tenantKey", "tenant_key");
        addAttribute(builder, user, "employeeNo", "employee_no");
        return builder.build();
    }

    @Override
    public UniWorkUser getUser(final String userId) {
        requireText(userId, "userId");
        JsonNode body = withTenantAccessToken("读取飞书成员", new TokenRequest() {
            @Override
            public HttpJsonResponse execute(String token) {
                String userPath = "/open-apis/contact/v3/users/" + UrlSupport.encode(userId);
                String url = UrlSupport.query(
                        UrlSupport.join(apiBaseUrl, userPath),
                        "user_id_type", userIdType);
                return http.get(url, bearerHeaders(token));
            }
        });
        JsonNode user = body.path("data").path("user");
        if (user.isMissingNode() || user.isNull()) {
            throw new UniWorkException("读取飞书成员失败：响应缺少 data.user");
        }
        String resolvedUserId = JsonSupport.firstText(user, "user_id", "open_id", "union_id");
        JsonNode avatar = user.path("avatar");
        UniWorkUser.Builder builder = UniWorkUser.builder(
                "feishu",
                resolvedUserId == null ? userId : resolvedUserId)
                .name(JsonSupport.firstText(user, "name", "en_name"))
                .avatarUrl(JsonSupport.firstText(
                        avatar,
                        "avatar_origin",
                        "avatar_640",
                        "avatar_240"))
                .email(JsonSupport.firstText(user, "email", "enterprise_email"))
                .mobile(JsonSupport.firstText(user, "mobile"));
        addAttribute(builder, user, "openId", "open_id");
        addAttribute(builder, user, "unionId", "union_id");
        addAttribute(builder, user, "employeeNo", "employee_no");
        return builder.build();
    }

    private SendResult sendMessage(
            final String receiver,
            final String messageType,
            final ObjectNode content) {
        JsonNode body = withTenantAccessToken("发送飞书消息", new TokenRequest() {
            @Override
            public HttpJsonResponse execute(String token) {
                ObjectNode request = JsonSupport.object();
                request.put("receive_id", receiver);
                request.put("msg_type", messageType);
                request.put("content", JsonSupport.write(content));
                String url = UrlSupport.query(
                        UrlSupport.join(apiBaseUrl, "/open-apis/im/v1/messages"),
                        "receive_id_type", receiveIdType);
                return http.post(url, bearerHeaders(token), request);
            }
        });
        JsonNode data = body.path("data");
        return SendResult.accepted(
                "feishu",
                JsonSupport.firstText(data, "message_id"),
                JsonSupport.firstText(body, "request_id"));
    }

    private JsonNode withTenantAccessToken(String operation, TokenRequest request) {
        for (int attempt = 0; attempt < 2; attempt++) {
            String token = getTenantAccessToken();
            HttpJsonResponse response = request.execute(token);
            JsonNode body = response.getBody();
            int errorCode = body.path("code").asInt(0);
            boolean tokenFailure = response.getStatusCode() == 401
                    || errorCode == 99991661
                    || errorCode == 99991663;
            if (tokenFailure && attempt == 0) {
                invalidateTenantAccessToken(token);
                continue;
            }
            requireHttpSuccess(response, operation);
            requireApiSuccess(body, operation);
            return body;
        }
        throw new UniWorkException(operation + "失败：tenant_access_token 刷新后仍不可用");
    }

    private String getTenantAccessToken() {
        AccessToken current = tenantAccessToken;
        long now = System.currentTimeMillis();
        if (current != null && current.expiresAtMillis > now) {
            return current.value;
        }
        synchronized (this) {
            current = tenantAccessToken;
            now = System.currentTimeMillis();
            if (current != null && current.expiresAtMillis > now) {
                return current.value;
            }
            ObjectNode request = JsonSupport.object();
            request.put("app_id", appId);
            request.put("app_secret", appSecret);
            HttpJsonResponse response = http.post(
                    UrlSupport.join(
                            apiBaseUrl,
                            "/open-apis/auth/v3/tenant_access_token/internal"),
                    request);
            requireHttpSuccess(response, "获取飞书 tenant_access_token");
            JsonNode body = response.getBody();
            requireApiSuccess(body, "获取飞书 tenant_access_token");
            String value = JsonSupport.firstText(body, "tenant_access_token");
            if (value == null) {
                throw new UniWorkException("飞书 tenant_access_token 响应缺少 tenant_access_token");
            }
            long expiresIn = body.path("expire").asLong(7200L);
            tenantAccessToken = new AccessToken(value, expiryMillis(now, expiresIn));
            return value;
        }
    }

    private synchronized void invalidateTenantAccessToken(String token) {
        if (tenantAccessToken != null && tenantAccessToken.value.equals(token)) {
            tenantAccessToken = null;
        }
    }

    private static ObjectNode textElement(String tag, String content) {
        ObjectNode text = JsonSupport.object();
        text.put("tag", tag);
        text.put("content", content);
        return text;
    }

    private static Map<String, String> bearerHeaders(String token) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Authorization", "Bearer " + token);
        return headers;
    }

    private static void requireApiSuccess(JsonNode body, String operation) {
        int code = body.path("code").asInt(0);
        if (code != 0) {
            String message = JsonSupport.firstText(body, "msg", "message");
            throw new UniWorkException(operation + "失败：code=" + code
                    + (message == null ? "" : "，msg=" + message));
        }
    }

    private static void requireOAuthSuccess(JsonNode body, String operation) {
        String error = JsonSupport.firstText(body, "error");
        if (error != null) {
            String description = JsonSupport.firstText(body, "error_description");
            throw new UniWorkException(operation + "失败：error=" + error
                    + (description == null ? "" : "，description=" + description));
        }
        requireApiSuccess(body, operation);
    }

    private static void requireHttpSuccess(HttpJsonResponse response, String operation) {
        if (!response.isSuccessful()) {
            JsonNode body = response.getBody();
            String code = JsonSupport.firstText(body, "code", "error");
            String message = JsonSupport.firstText(
                    body,
                    "msg",
                    "message",
                    "error_description");
            throw new UniWorkException(operation + "失败：HTTP " + response.getStatusCode()
                    + (code == null ? "" : "，code=" + code)
                    + (message == null ? "" : "，message=" + message));
        }
    }

    private static long expiryMillis(long now, long expiresInSeconds) {
        long safeSeconds = Math.max(1L, expiresInSeconds);
        long margin = Math.min(300L, safeSeconds / 5L);
        return now + (safeSeconds - margin) * 1000L;
    }

    private static void addAttribute(
            UniWorkUser.Builder builder,
            JsonNode source,
            String attributeName,
            String fieldName) {
        String value = JsonSupport.firstText(source, fieldName);
        if (value != null) {
            builder.attribute(attributeName, value);
        }
    }

    private static String requireConfigured(String value, String key) {
        if (value == null || value.trim().isEmpty()) {
            throw new UniWorkException("使用飞书登录前请配置 uniwork.feishu." + key);
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private interface TokenRequest {
        HttpJsonResponse execute(String token);
    }

    private static final class AccessToken {
        private final String value;
        private final long expiresAtMillis;

        private AccessToken(String value, long expiresAtMillis) {
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
