package com.idongxia.uniwork.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.idongxia.uniwork.UniWorkException;

/**
 * 平台适配器共用的轻量 JSON 工具，业务项目通常不需要直接调用。
 * Lightweight JSON helpers shared by platform adapters; applications normally do not call this class.
 */
public final class JsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSupport() {
    }

    /** 创建 JSON 对象节点。Creates a JSON object node. */
    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    /** 创建 JSON 数组节点。Creates a JSON array node. */
    public static ArrayNode array() {
        return MAPPER.createArrayNode();
    }

    /**
     * 将 JSON 字符串解析为树结构。
     * Parses JSON text into a tree.
     */
    public static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new UniWorkException("第三方平台返回了无法解析的 JSON", e);
        }
    }

    /**
     * 将 JSON 节点序列化为字符串。
     * Serializes a JSON node into text.
     */
    public static String write(JsonNode json) {
        try {
            return MAPPER.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new UniWorkException("生成第三方平台 JSON 请求失败", e);
        }
    }

    /**
     * 按候选字段顺序读取第一个非空文本。
     * Returns the first non-blank textual value from the candidate field names.
     */
    public static String firstText(JsonNode json, String... fieldNames) {
        if (json == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = json.get(fieldName);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (text != null && !text.trim().isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }
}
