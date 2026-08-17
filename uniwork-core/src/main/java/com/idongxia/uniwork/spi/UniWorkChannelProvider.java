package com.idongxia.uniwork.spi;

import com.idongxia.uniwork.channel.UniWorkChannel;
import com.idongxia.uniwork.config.UniWorkConfig;

/**
 * 内置和第三方渠道通过 Java {@link java.util.ServiceLoader} 接入的扩展点。
 * Java {@link java.util.ServiceLoader} extension point for built-in and third-party channels.
 */
public interface UniWorkChannelProvider<T extends UniWorkChannel> {

    /** 返回 {@code uniwork} 根节点下的配置段名称。Returns the section name below {@code uniwork}. */
    String configurationPrefix();

    /** 返回调用方传给 {@code uniWork.platform(...)} 的渠道接口类型。Returns the channel interface type. */
    Class<T> channelType();

    /** 使用已经裁剪到本渠道的配置创建实例。Creates a channel from its scoped configuration section. */
    T create(UniWorkConfig config);
}
