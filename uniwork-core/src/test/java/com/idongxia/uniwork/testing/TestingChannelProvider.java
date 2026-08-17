package com.idongxia.uniwork.testing;

import com.idongxia.uniwork.config.UniWorkConfig;
import com.idongxia.uniwork.spi.UniWorkChannelProvider;

public final class TestingChannelProvider implements UniWorkChannelProvider<TestingChannel> {

    @Override
    public String configurationPrefix() {
        return "testing";
    }

    @Override
    public Class<TestingChannel> channelType() {
        return TestingChannel.class;
    }

    @Override
    public TestingChannel create(UniWorkConfig config) {
        return new DefaultTestingChannel(config.required("prefix"));
    }
}
