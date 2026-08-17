package com.idongxia.uniwork.spi;

import com.idongxia.uniwork.channel.UniWorkChannel;
import com.idongxia.uniwork.config.UniWorkConfig;

/**
 * Java ServiceLoader extension point used by built-in and third-party channels.
 */
public interface UniWorkChannelProvider<T extends UniWorkChannel> {

    /** Configuration section below the root {@code uniwork} key. */
    String configurationPrefix();

    /** The type callers pass to {@code uniWork.platform(...)}. */
    Class<T> channelType();

    /** Creates a channel from its already-scoped configuration section. */
    T create(UniWorkConfig config);
}
