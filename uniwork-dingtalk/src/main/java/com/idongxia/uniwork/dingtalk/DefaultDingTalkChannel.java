package com.idongxia.uniwork.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.idongxia.uniwork.SendResult;
import com.idongxia.uniwork.UniWorkException;
import com.idongxia.uniwork.UniWorkUser;
import com.idongxia.uniwork.channel.DingTalkChannel;
import com.idongxia.uniwork.support.HttpJsonClient;
import com.idongxia.uniwork.support.HttpJsonResponse;
import com.idongxia.uniwork.support.JsonSupport;
import com.idongxia.uniwork.support.UrlSupport;

import java.util.LinkedHashMap;
import java.util.Map;

/** 钉钉企业内部应用的默认 Java 8 实现。Default Java 8 implementation for a DingTalk internal app. */
final class DefaultDingTalkChannel implements DingTalkChannel {

    private final String clientId;
    private final String clientSecret;
    private final long agentId;
    private final String redirectUri;
    private final String oauthScope;
    private final String oauthState;
    private final String cardButtonText;
    private final String apiBaseUrl;
    private final String legacyApiBaseUrl;
    private final String loginBaseUrl;
    private final HttpJsonClient http;
    private volatile AccessToken appAccessToken;

    DefaultDingTalkChannel(
            String clientId,
            String clientSecret,
            long agentId,
            String redirectUri,
            String oauthScope,
            String oauthState,
            String cardButtonText,
            String apiBaseUrl,
            String legacyApiBaseUrl,
            String loginBaseUrl,
            int connectTimeoutMillis,
            int readTimeoutMillis) {
        this.clientId = requireText(clientId, "clientId");
        this.clientSecret = requireText(clientSecret, "clientSecret");
        this.agentId = agentId;
        this.redirectUri = redirectUri;
        this.oauthScope = requireText(oauthScope, "oauthScope");
        this.oauthState = requireText(oauthState, "oauthState");
        this.cardButtonText = requireText(cardButtonText, "cardButtonText");
        this.apiBaseUrl = requireText(apiBaseUrl, "apiBaseUrl");
        this.legacyApiBaseUrl = requireText(legacyApiBaseUrl, "legacyApiBaseUrl");
        this.loginBaseUrl = requireText(loginBaseUrl, "loginBaseUrl");
        this.http = new HttpJsonClient(connectTimeoutMillis, readTimeoutMillis);
    }

    @Override
    public SendResult sendContent(String receiver, String content) {
        requireText(receiver, "receiver");
        ObjectNode text = JsonSupport.object();
        text.put("content", requireText(content, "content"));
        ObjectNode message = JsonSupport.object();
        message.put("msgtype", "text");
        message.set("text", text);
        return sendWorkNotification(receiver, message);
    }

    @Override
    public SendResult sendContent(String receiver, String title, String content) {
        return sendContent(
                receiver,
                requireText(title, "title") + "\n" + requireText(content, "content"));
    }

    @Override
    public SendResult sendCard(String receiver, String title, String content, String url) {
        requireText(receiver, "receiver");
        ObjectNode card = JsonSupport.object();
        card.put("title", requireText(title, "title"));
        card.put("markdown", requireText(content, "content"));
        card.put("single_title", cardButtonText);
        card.put("single_url", requireText(url, "url"));
        ObjectNode message = JsonSupport.object();
        message.put("msgtype", "action_card");
        message.set("action_card", card);
        return sendWorkNotification(receiver, message);
    }

    @Override
    public String loginUrl() {
        return UrlSupport.query(
                UrlSupport.join(loginBaseUrl, "/oauth2/auth"),
                "client_id", clientId,
                "redirect_uri", requireConfigured(redirectUri, "redirect-uri"),
                "state", oauthState,
                "response_type", "code",
                "prompt", "consent",
                "scope", oauthScope);
    }

    @Override
    public UniWorkUser login(String code) {
        requireText(code, "code");
        ObjectNode request = JsonSupport.object();
        request.put("clientId", clientId);
        request.put("clientSecret", clientSecret);
        request.put("code", code);
        request.put("grantType", "authorization_code");
        HttpJsonResponse tokenResponse = http.post(
                UrlSupport.join(apiBaseUrl, "/v1.0/oauth2/userAccessToken"),
                request);
        requireHttpSuccess(tokenResponse, "获取钉钉用户 access_token");
        JsonNode tokenBody = tokenResponse.getBody();
        requireModernApiSuccess(tokenBody, "获取钉钉用户 access_token");
        String userToken = JsonSupport.firstText(tokenBody, "accessToken", "access_token");
        if (userToken == null) {
            throw new UniWorkException("钉钉用户 access_token 响应缺少 accessToken");
        }

        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("x-acs-dingtalk-access-token", userToken);
        HttpJsonResponse userResponse = http.get(
                UrlSupport.join(apiBaseUrl, "/v1.0/contact/users/me"),
                headers);
        requireHttpSuccess(userResponse, "读取钉钉登录用户");
        JsonNode user = userResponse.getBody();
        requireModernApiSuccess(user, "读取钉钉登录用户");
        String identity = JsonSupport.firstText(user, "userId", "unionId", "openId");
        if (identity == null) {
            throw new UniWorkException("钉钉登录结果中没有 userId、unionId 或 openId");
        }
        UniWorkUser.Builder builder = UniWorkUser.builder("dingtalk", identity)
                .name(JsonSupport.firstText(user, "nick", "name"))
                .avatarUrl(JsonSupport.firstText(user, "avatarUrl", "avatar"))
                .email(JsonSupport.firstText(user, "email"))
                .mobile(JsonSupport.firstText(user, "mobile"));
        addAttribute(builder, user, "openId", "openId");
        addAttribute(builder, user, "unionId", "unionId");
        addAttribute(builder, user, "corpId", "corpId");
        addAttribute(builder, user, "stateCode", "stateCode");
        return builder.build();
    }

    @Override
    public UniWorkUser getUser(final String userId) {
        requireText(userId, "userId");
        JsonNode response = withAppAccessToken("读取钉钉成员", new TokenRequest() {
            @Override
            public HttpJsonResponse execute(String token) {
                ObjectNode request = JsonSupport.object();
                request.put("userid", userId);
                request.put("language", "zh_CN");
                String url = UrlSupport.query(
                        UrlSupport.join(legacyApiBaseUrl, "/topapi/v2/user/get"),
                        "access_token", token);
                return http.post(url, request);
            }
        });
        JsonNode user = response.path("result");
        if (user.isMissingNode() || user.isNull()) {
            throw new UniWorkException("读取钉钉成员失败：响应缺少 result");
        }
        String resolvedUserId = JsonSupport.firstText(user, "userid");
        UniWorkUser.Builder builder = UniWorkUser.builder(
                "dingtalk",
                resolvedUserId == null ? userId : resolvedUserId)
                .name(JsonSupport.firstText(user, "name"))
                .avatarUrl(JsonSupport.firstText(user, "avatar"))
                .email(JsonSupport.firstText(user, "email", "org_email"))
                .mobile(JsonSupport.firstText(user, "mobile"));
        addAttribute(builder, user, "unionId", "unionid");
        addAttribute(builder, user, "jobNumber", "job_number");
        addAttribute(builder, user, "title", "title");
        JsonNode departments = user.get("dept_id_list");
        if (departments != null && departments.isArray()) {
            builder.attribute("departments", joinArray(departments));
        }
        return builder.build();
    }

    private SendResult sendWorkNotification(final String receiver, final ObjectNode message) {
        JsonNode result = withAppAccessToken("发送钉钉工作通知", new TokenRequest() {
            @Override
            public HttpJsonResponse execute(String token) {
                ObjectNode request = JsonSupport.object();
                request.put("agent_id", agentId);
                request.put("userid_list", receiver);
                request.put("to_all_user", false);
                request.set("msg", message);
                String url = UrlSupport.query(
                        UrlSupport.join(
                                legacyApiBaseUrl,
                                "/topapi/message/corpconversation/asyncsend_v2"),
                        "access_token", token);
                return http.post(url, request);
            }
        });
        String taskId = JsonSupport.firstText(result, "task_id");
        if (taskId == null && result.has("task_id")) {
            taskId = result.get("task_id").asText();
        }
        return SendResult.accepted(
                "dingtalk",
                taskId,
                JsonSupport.firstText(result, "request_id"));
    }

    private JsonNode withAppAccessToken(String operation, TokenRequest request) {
        for (int attempt = 0; attempt < 2; attempt++) {
            String token = getAppAccessToken();
            HttpJsonResponse response = request.execute(token);
            JsonNode body = response.getBody();
            int errorCode = body.path("errcode").asInt(0);
            boolean tokenFailure = response.getStatusCode() == 401
                    || errorCode == 88
                    || errorCode == 40014
                    || errorCode == 42001;
            if (tokenFailure && attempt == 0) {
                invalidateAppAccessToken(token);
                continue;
            }
            requireHttpSuccess(response, operation);
            requireLegacyApiSuccess(body, operation);
            return body;
        }
        throw new UniWorkException(operation + "失败：access_token 刷新后仍不可用");
    }

    private String getAppAccessToken() {
        AccessToken current = appAccessToken;
        long now = System.currentTimeMillis();
        if (current != null && current.expiresAtMillis > now) {
            return current.value;
        }
        synchronized (this) {
            current = appAccessToken;
            now = System.currentTimeMillis();
            if (current != null && current.expiresAtMillis > now) {
                return current.value;
            }
            ObjectNode request = JsonSupport.object();
            request.put("appKey", clientId);
            request.put("appSecret", clientSecret);
            HttpJsonResponse response = http.post(
                    UrlSupport.join(apiBaseUrl, "/v1.0/oauth2/accessToken"),
                    request);
            requireHttpSuccess(response, "获取钉钉应用 access_token");
            JsonNode body = response.getBody();
            requireModernApiSuccess(body, "获取钉钉应用 access_token");
            String value = JsonSupport.firstText(body, "accessToken", "access_token");
            if (value == null) {
                throw new UniWorkException("钉钉应用 access_token 响应缺少 accessToken");
            }
            long expiresIn = body.path("expireIn").asLong(
                    body.path("expires_in").asLong(7200L));
            appAccessToken = new AccessToken(value, expiryMillis(now, expiresIn));
            return value;
        }
    }

    private synchronized void invalidateAppAccessToken(String token) {
        if (appAccessToken != null && appAccessToken.value.equals(token)) {
            appAccessToken = null;
        }
    }

    private static void requireLegacyApiSuccess(JsonNode body, String operation) {
        int errorCode = body.path("errcode").asInt(0);
        if (errorCode != 0) {
            String message = JsonSupport.firstText(body, "errmsg");
            throw new UniWorkException(operation + "失败：errcode=" + errorCode
                    + (message == null ? "" : "，errmsg=" + message));
        }
    }

    private static void requireModernApiSuccess(JsonNode body, String operation) {
        String code = JsonSupport.firstText(body, "code");
        if (code != null && !"0".equals(code)) {
            String message = JsonSupport.firstText(body, "message", "msg");
            throw new UniWorkException(operation + "失败：code=" + code
                    + (message == null ? "" : "，message=" + message));
        }
    }

    private static void requireHttpSuccess(HttpJsonResponse response, String operation) {
        if (!response.isSuccessful()) {
            JsonNode body = response.getBody();
            String code = JsonSupport.firstText(body, "code", "errcode");
            String message = JsonSupport.firstText(body, "message", "errmsg");
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

    private static String joinArray(JsonNode array) {
        StringBuilder result = new StringBuilder();
        for (JsonNode value : array) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(value.asText());
        }
        return result.toString();
    }

    private static String requireConfigured(String value, String key) {
        if (value == null || value.trim().isEmpty()) {
            throw new UniWorkException("使用钉钉登录前请配置 uniwork.dingtalk." + key);
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
