package com.idongxia.uniwork.channel;

import com.idongxia.uniwork.UniWorkUser;

/**
 * Shared user and login capabilities for enterprise collaboration platforms.
 */
public interface CollaborationChannel extends UniWorkChannel {

    String loginUrl();

    UniWorkUser login(String code);

    UniWorkUser getUser(String userId);
}
