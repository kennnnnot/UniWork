package com.idongxia.uniwork;

/**
 * A successful channel submission. Callers may ignore this value when they do
 * not need a platform message id or request id.
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

    public static SendResult accepted(String platform) {
        return new SendResult(requireText(platform, "platform"), null, null);
    }

    public static SendResult accepted(String platform, String messageId, String requestId) {
        return new SendResult(requireText(platform, "platform"), messageId, requestId);
    }

    public String getPlatform() {
        return platform;
    }

    public String getMessageId() {
        return messageId;
    }

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
