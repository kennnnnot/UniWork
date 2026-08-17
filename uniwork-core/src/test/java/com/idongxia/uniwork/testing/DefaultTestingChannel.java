package com.idongxia.uniwork.testing;

import com.idongxia.uniwork.SendResult;

final class DefaultTestingChannel implements TestingChannel {

    private final String prefix;

    DefaultTestingChannel(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public SendResult sendContent(String receiver, String content) {
        return SendResult.accepted(prefix, receiver + ":" + content, null);
    }

    @Override
    public SendResult sendContent(String receiver, String title, String content) {
        return SendResult.accepted(prefix, receiver + ":" + title + ":" + content, null);
    }

    @Override
    public String getPrefix() {
        return prefix;
    }
}
