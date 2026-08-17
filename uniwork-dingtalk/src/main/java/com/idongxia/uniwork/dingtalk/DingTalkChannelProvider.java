package com.idongxia.uniwork.dingtalk;

import com.idongxia.uniwork.UniWorkException;
import com.idongxia.uniwork.channel.DingTalkChannel;
import com.idongxia.uniwork.config.UniWorkConfig;
import com.idongxia.uniwork.spi.UniWorkChannelProvider;

/**
 * 从 {@code uniwork.dingtalk} 配置创建钉钉渠道。
 * Creates a DingTalk channel from the {@code uniwork.dingtalk} configuration section.
 */
public final class DingTalkChannelProvider implements UniWorkChannelProvider<DingTalkChannel> {

    @Override
    public String configurationPrefix() {
        return "dingtalk";
    }

    @Override
    public Class<DingTalkChannel> channelType() {
        return DingTalkChannel.class;
    }

    @Override
    public DingTalkChannel create(UniWorkConfig config) {
        return new DefaultDingTalkChannel(
                requiredAny(config, "client-id", "app-key"),
                requiredAny(config, "client-secret", "app-secret"),
                positiveLong(config.required("agent-id"), "agent-id"),
                config.get("redirect-uri"),
                config.get("oauth-scope", "openid"),
                config.get("oauth-state", "uniwork"),
                config.get("card-button-text", "查看详情"),
                config.get("api-base-url", "https://api.dingtalk.com"),
                config.get("legacy-api-base-url", "https://oapi.dingtalk.com"),
                config.get("login-base-url", "https://login.dingtalk.com"),
                config.getInt("connect-timeout-millis", 3000),
                config.getInt("read-timeout-millis", 5000));
    }

    private static String requiredAny(UniWorkConfig config, String first, String second) {
        String value = config.get(first);
        if (value == null || value.trim().isEmpty()) {
            value = config.get(second);
        }
        if (value == null || value.trim().isEmpty()) {
            throw new UniWorkException("缺少 UniWork 配置项：" + first + "（兼容名称：" + second + "）");
        }
        return value;
    }

    private static long positiveLong(String value, String key) {
        try {
            long number = Long.parseLong(value);
            if (number <= 0) {
                throw new NumberFormatException("not positive");
            }
            return number;
        } catch (NumberFormatException e) {
            throw new UniWorkException("UniWork 配置项不是有效正整数：" + key, e);
        }
    }
}
