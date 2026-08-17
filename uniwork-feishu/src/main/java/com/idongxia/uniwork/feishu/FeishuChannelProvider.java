package com.idongxia.uniwork.feishu;

import com.idongxia.uniwork.channel.FeishuChannel;
import com.idongxia.uniwork.config.UniWorkConfig;
import com.idongxia.uniwork.spi.UniWorkChannelProvider;

/**
 * 从 {@code uniwork.feishu} 配置创建飞书渠道。
 * Creates a Feishu channel from the {@code uniwork.feishu} configuration section.
 */
public final class FeishuChannelProvider implements UniWorkChannelProvider<FeishuChannel> {

    @Override
    public String configurationPrefix() {
        return "feishu";
    }

    @Override
    public Class<FeishuChannel> channelType() {
        return FeishuChannel.class;
    }

    @Override
    public FeishuChannel create(UniWorkConfig config) {
        return new DefaultFeishuChannel(
                config.required("app-id"),
                config.required("app-secret"),
                config.get("redirect-uri"),
                config.get("oauth-scope", "contact:contact.base:readonly"),
                config.get("oauth-state", "uniwork"),
                config.get("receive-id-type", "user_id"),
                config.get("user-id-type", "user_id"),
                config.get("card-button-text", "查看详情"),
                config.get("api-base-url", "https://open.feishu.cn"),
                config.get("accounts-base-url", "https://accounts.feishu.cn"),
                config.getInt("connect-timeout-millis", 3000),
                config.getInt("read-timeout-millis", 5000));
    }
}
