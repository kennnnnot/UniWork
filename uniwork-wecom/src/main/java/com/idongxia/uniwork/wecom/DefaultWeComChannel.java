package com.idongxia.uniwork.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.idongxia.uniwork.SendResult;
import com.idongxia.uniwork.UniWorkException;
import com.idongxia.uniwork.UniWorkUser;
import com.idongxia.uniwork.channel.WeComChannel;
import com.idongxia.uniwork.support.HttpJsonClient;
import com.idongxia.uniwork.support.HttpJsonResponse;
import com.idongxia.uniwork.support.JsonSupport;
import com.idongxia.uniwork.support.UrlSupport;

/** 企业微信自建应用的默认 Java 8 实现。Default Java 8 implementation for a WeCom custom app. */
final class DefaultWeComChannel implements WeComChannel {

    private final String corpId;
    private final long agentId;
    private final String secret;
    private final String redirectUri;
    private final String oauthScope;
    private final String oauthState;
    private final String cardButtonText;
    private final String apiBaseUrl;
    private final String oauthBaseUrl;
    private final HttpJsonClient http;
    private volatile AccessToken accessToken;

    DefaultWeComChannel(
            String corpId,
            long agentId,
            String secret,
            String redirectUri,
            String oauthScope,
            String oauthState,
            String cardButtonText,
            String apiBaseUrl,
            String oauthBaseUrl,
            int connectTimeoutMillis,
            int readTimeoutMillis) {
        this.corpId = requireText(corpId, "corpId");
        this.agentId = agentId;
        this.secret = requireText(secret, "secret");
        this.redirectUri = redirectUri;
        this.oauthScope = requireText(oauthScope, "oauthScope");
        this.oauthState = requireText(oauthState, "oauthState");
        this.cardButtonText = requireText(cardButtonText, "cardButtonText");
        this.apiBaseUrl = requireText(apiBaseUrl, "apiBaseUrl");
        this.oauthBaseUrl = requireText(oauthBaseUrl, "oauthBaseUrl");
        this.http = new HttpJsonClient(connectTimeoutMillis, readTimeoutMillis);
    }

    @Override
    public SendResult sendContent(String receiver, String content) {
        requireText(receiver, "receiver");
        requireText(content, "content");
        ObjectNode message = baseMessage(receiver, "text");
        message.set("text", JsonSupport.object().put("content", content));
        return sendMessage(message);
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
        ObjectNode message = baseMessage(receiver, "textcard");
        ObjectNode card = JsonSupport.object();
        card.put("title", requireText(title, "title"));
        card.put("description", requireText(content, "content"));
        card.put("url", requireText(url, "url"));
        card.put("btntxt", cardButtonText);
        message.set("textcard", card);
        return sendMessage(message);
    }

    @Override
    public String loginUrl() {
        String callback = requireConfigured(redirectUri, "redirect-uri");
        return UrlSupport.query(
                UrlSupport.join(oauthBaseUrl, "/connect/oauth2/authorize"),
                "appid", corpId,
                "redirect_uri", callback,
                "response_type", "code",
                "scope", oauthScope,
                "state", oauthState,
                "agentid", String.valueOf(agentId))
                + "#wechat_redirect";
    }

    @Override
    public UniWorkUser login(final String code) {
        requireText(code, "code");
        JsonNode identity = withAccessToken("获取企业微信登录身份", new TokenRequest() {
            @Override
            public HttpJsonResponse execute(String token) {
                String url = UrlSupport.query(
                        UrlSupport.join(apiBaseUrl, "/cgi-bin/auth/getuserinfo"),
                        "access_token", token,
                        "code", code);
                return http.get(url);
            }
        });
        String userId = JsonSupport.firstText(identity, "userid", "UserId", "user_id");
        if (userId != null) {
            return getUser(userId);
        }
        String openId = JsonSupport.firstText(identity, "openid", "OpenId", "open_id");
        if (openId != null) {
            return UniWorkUser.builder("wecom", openId)
                    .attribute("identityType", "openid")
                    .build();
        }
        throw new UniWorkException("企业微信登录结果中没有 userid 或 openid");
    }

    @Override
    public UniWorkUser getUser(final String userId) {
        requireText(userId, "userId");
        JsonNode user = withAccessToken("读取企业微信成员", new TokenRequest() {
            @Override
            public HttpJsonResponse execute(String token) {
                String url = UrlSupport.query(
                        UrlSupport.join(apiBaseUrl, "/cgi-bin/user/get"),
                        "access_token", token,
                        "userid", userId);
                return http.get(url);
            }
        });
        String resolvedUserId = JsonSupport.firstText(user, "userid", "user_id");
        UniWorkUser.Builder builder = UniWorkUser.builder(
                "wecom",
                resolvedUserId == null ? userId : resolvedUserId)
                .name(JsonSupport.firstText(user, "name"))
                .avatarUrl(JsonSupport.firstText(user, "avatar", "thumb_avatar"))
                .email(JsonSupport.firstText(user, "email", "biz_mail"))
                .mobile(JsonSupport.firstText(user, "mobile"));
        addAttribute(builder, user, "position", "position");
        addAttribute(builder, user, "gender", "gender");
        addAttribute(builder, user, "status", "status");
        addAttribute(builder, user, "mainDepartment", "main_department");
        JsonNode departments = user.get("department");
        if (departments != null && departments.isArray()) {
            builder.attribute("departments", joinArray(departments));
        }
        return builder.build();
    }

    private SendResult sendMessage(final ObjectNode message) {
        JsonNode result = withAccessToken("发送企业微信消息", new TokenRequest() {
            @Override
            public HttpJsonResponse execute(String token) {
                String url = UrlSupport.query(
                        UrlSupport.join(apiBaseUrl, "/cgi-bin/message/send"),
                        "access_token", token);
                return http.post(url, message);
            }
        });
        String invalidUsers = JsonSupport.firstText(result, "invaliduser");
        if (invalidUsers != null) {
            throw new UniWorkException("企业微信消息存在无效接收人：" + invalidUsers);
        }
        return SendResult.accepted(
                "wecom",
                JsonSupport.firstText(result, "msgid"),
                JsonSupport.firstText(result, "response_code"));
    }

    private ObjectNode baseMessage(String receiver, String messageType) {
        ObjectNode message = JsonSupport.object();
        message.put("touser", receiver);
        message.put("msgtype", messageType);
        message.put("agentid", agentId);
        return message;
    }

    private JsonNode withAccessToken(String operation, TokenRequest request) {
        for (int attempt = 0; attempt < 2; attempt++) {
            String token = getAccessToken();
            HttpJsonResponse response = request.execute(token);
            JsonNode body = response.getBody();
            int errorCode = body.path("errcode").asInt(0);
            boolean tokenFailure = response.getStatusCode() == 401
                    || errorCode == 40014
                    || errorCode == 42001;
            if (tokenFailure && attempt == 0) {
                invalidateAccessToken(token);
                continue;
            }
            requireHttpSuccess(response, operation);
            requireApiSuccess(body, operation);
            return body;
        }
        throw new UniWorkException(operation + "失败：access_token 刷新后仍不可用");
    }

    private String getAccessToken() {
        AccessToken current = accessToken;
        long now = System.currentTimeMillis();
        if (current != null && current.expiresAtMillis > now) {
            return current.value;
        }
        synchronized (this) {
            current = accessToken;
            now = System.currentTimeMillis();
            if (current != null && current.expiresAtMillis > now) {
                return current.value;
            }
            String url = UrlSupport.query(
                    UrlSupport.join(apiBaseUrl, "/cgi-bin/gettoken"),
                    "corpid", corpId,
                    "corpsecret", secret);
            HttpJsonResponse response = http.get(url);
            requireHttpSuccess(response, "获取企业微信 access_token");
            JsonNode body = response.getBody();
            requireApiSuccess(body, "获取企业微信 access_token");
            String value = JsonSupport.firstText(body, "access_token");
            if (value == null) {
                throw new UniWorkException("企业微信 access_token 响应缺少 access_token");
            }
            long expiresIn = body.path("expires_in").asLong(7200L);
            accessToken = new AccessToken(value, expiryMillis(now, expiresIn));
            return value;
        }
    }

    private synchronized void invalidateAccessToken(String token) {
        if (accessToken != null && accessToken.value.equals(token)) {
            accessToken = null;
        }
    }

    private static void requireApiSuccess(JsonNode body, String operation) {
        int errorCode = body.path("errcode").asInt(0);
        if (errorCode != 0) {
            String message = JsonSupport.firstText(body, "errmsg");
            throw new UniWorkException(operation + "失败：errcode=" + errorCode
                    + (message == null ? "" : "，errmsg=" + message));
        }
    }

    private static void requireHttpSuccess(HttpJsonResponse response, String operation) {
        if (!response.isSuccessful()) {
            throw new UniWorkException(operation + "失败：HTTP " + response.getStatusCode());
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
        JsonNode value = source.get(fieldName);
        if (value != null && !value.isNull()) {
            builder.attribute(attributeName, value.asText());
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
            throw new UniWorkException("使用企业微信登录前请配置 uniwork.wecom." + key);
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
