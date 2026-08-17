package com.idongxia.uniwork;

import com.idongxia.uniwork.testing.TestingChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniWorkTest {

    @Test
    void loadsAConfiguredServiceProvider() {
        UniWork uniWork = UniWork.load();

        assertTrue(uniWork.hasPlatform(TestingChannel.class));
        assertEquals("yaml", uniWork.platform(TestingChannel.class).getPrefix());
        assertEquals(
                "receiver:content",
                uniWork.platform(TestingChannel.class)
                        .sendContent("receiver", "content")
                        .getMessageId());
    }

    @Test
    void ignoresProvidersWithoutAConfigurationSection() {
        UniWork uniWork = UniWork.load("uniwork.properties");

        assertTrue(uniWork.hasPlatform(TestingChannel.class));
        assertFalse(uniWork.hasPlatform(com.idongxia.uniwork.channel.WeComChannel.class));
    }

    @Test
    void reportsAPlainConfigurationErrorForMissingBuiltInChannels() {
        UniWork uniWork = UniWork.load();

        UniWorkException exception = assertThrows(
                UniWorkException.class,
                uniWork::wecom);

        assertEquals("企业微信未配置，请检查 uniwork.wecom 配置", exception.getMessage());
    }
}
