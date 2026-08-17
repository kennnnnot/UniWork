package com.idongxia.uniwork.all;

import com.idongxia.uniwork.UniWork;
import com.idongxia.uniwork.channel.DingTalkChannel;
import com.idongxia.uniwork.channel.FeishuChannel;
import com.idongxia.uniwork.channel.WeComChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UniWorkAllTest {

    @Test
    void discoversAllConfiguredPlatformAdapters() {
        UniWork uniWork = UniWork.load();
        try {
            assertTrue(uniWork.hasPlatform(WeComChannel.class));
            assertTrue(uniWork.hasPlatform(DingTalkChannel.class));
            assertTrue(uniWork.hasPlatform(FeishuChannel.class));
        } finally {
            uniWork.close();
        }
    }
}
