package com.idongxia.uniwork.channel;

import com.idongxia.uniwork.SendResult;

/**
 * The smallest contract every built-in or custom delivery channel implements.
 */
public interface UniWorkChannel extends AutoCloseable {

    SendResult sendContent(String receiver, String content);

    SendResult sendContent(String receiver, String title, String content);

    @Override
    default void close() {
        // Most HTTP-based channels have nothing to release.
    }
}
