package com.idongxia.uniwork.support;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 第三方平台 HTTP JSON 响应，只保留状态码、解析结果和受限长度的原文。
 * HTTP JSON response containing the status, parsed tree, and size-limited raw body.
 */
public final class HttpJsonResponse {

    private final int statusCode;
    private final JsonNode body;
    private final String bodyText;

    HttpJsonResponse(int statusCode, JsonNode body, String bodyText) {
        this.statusCode = statusCode;
        this.body = body;
        this.bodyText = bodyText;
    }

    /** 返回 HTTP 状态码。Returns the HTTP status code. */
    public int getStatusCode() {
        return statusCode;
    }

    /** 返回解析后的 JSON。Returns the parsed JSON body. */
    public JsonNode getBody() {
        return body;
    }

    /** 返回受限长度的响应原文。Returns the size-limited raw body. */
    public String getBodyText() {
        return bodyText;
    }

    /** 判断 HTTP 状态是否为 2xx。Returns whether the HTTP status is 2xx. */
    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
