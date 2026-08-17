package com.idongxia.uniwork.channel;

import com.idongxia.uniwork.SendResult;

/**
 * 所有内置或自定义消息渠道都要实现的最小契约。
 * Smallest contract implemented by every built-in or custom delivery channel.
 */
public interface UniWorkChannel extends AutoCloseable {

    /**
     * 向接收人发送纯文本内容。
     * Sends plain text content to a receiver.
     */
    SendResult sendContent(String receiver, String content);

    /**
     * 向接收人发送带标题的内容；不支持独立标题的平台会将标题放在正文首行。
     * Sends titled content; platforms without a separate title place it on the first content line.
     */
    SendResult sendContent(String receiver, String title, String content);

    /** 关闭渠道资源；普通 HTTP 渠道默认无需处理。Closes channel resources; plain HTTP channels do nothing by default. */
    @Override
    default void close() {
        // 普通 HTTP 渠道没有需要释放的长连接资源。Most HTTP channels have no persistent resources.
    }
}
