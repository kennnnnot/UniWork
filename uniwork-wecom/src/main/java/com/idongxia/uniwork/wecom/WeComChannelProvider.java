package com.idongxia.uniwork.wecom;

import com.idongxia.uniwork.UniWorkException;
import com.idongxia.uniwork.channel.WeComChannel;
import com.idongxia.uniwork.config.UniWorkConfig;
import com.idongxia.uniwork.spi.UniWorkChannelProvider;

/**
 * 从 {@code uniwork.wecom} 配置创建企业微信渠道。
 * Creates a WeCom channel from the {@code uniwork.wecom} configuration section.
 */
public final class WeComChannelProvider implements UniWorkChannelProvider<WeComChannel> {

    @Override
    public String configurationPrefix() {
        return "wecom";
    }

    @Override
    public Class<WeComChannel> channelType() {
        return WeComChannel.class;
    }

    @Override
    public WeComChannel create(UniWorkConfig config) {
        return new DefaultWeComChannel(
                config.required("corp-id"),
                positiveLong(config.required("agent-id"), "agent-id"),
                config.required("secret"),
                config.get("redirect-uri"),
                config.get("oauth-scope", "snsapi_base"),
                config.get("oauth-state", "uniwork"),
                config.get("card-button-text", "查看详情"),
                config.get("api-base-url", "https://qyapi.weixin.qq.com"),
                config.get("oauth-base-url", "https://open.weixin.qq.com"),
                config.getInt("connect-timeout-millis", 3000),
                config.getInt("read-timeout-millis", 5000));
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
