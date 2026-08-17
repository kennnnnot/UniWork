package com.idongxia.uniwork;

/**
 * 消息被第三方平台受理后的结果；业务不关心消息 ID 时可以忽略返回值。
 * Result returned after a third-party platform accepts a message; callers may ignore it when IDs are unnecessary.
 */
public final class SendResult {

    private final String platform;
    private final String messageId;
    private final String requestId;

    private SendResult(String platform, String messageId, String requestId) {
        this.platform = platform;
        this.messageId = messageId;
        this.requestId = requestId;
    }

    /**
     * 创建一个仅包含平台名称的受理结果。
     * Creates an acceptance result containing only the platform name.
     *
     * @param platform 平台标识；platform identifier
     * @return 受理结果；acceptance result
     */
    public static SendResult accepted(String platform) {
        return new SendResult(requireText(platform, "platform"), null, null);
    }

    /**
     * 创建包含平台消息 ID 和请求 ID 的受理结果。
     * Creates an acceptance result with platform message and request IDs.
     *
     * @param platform 平台标识；platform identifier
     * @param messageId 平台消息 ID，可能为空；platform message ID, possibly null
     * @param requestId 平台请求 ID，可能为空；platform request ID, possibly null
     * @return 受理结果；acceptance result
     */
    public static SendResult accepted(String platform, String messageId, String requestId) {
        return new SendResult(requireText(platform, "platform"), messageId, requestId);
    }

    /** 返回平台标识。Returns the platform identifier. */
    public String getPlatform() {
        return platform;
    }

    /** 返回平台消息 ID，平台未提供时为 {@code null}。Returns the platform message ID, or {@code null}. */
    public String getMessageId() {
        return messageId;
    }

    /** 返回平台请求 ID，平台未提供时为 {@code null}。Returns the platform request ID, or {@code null}. */
    public String getRequestId() {
        return requestId;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
