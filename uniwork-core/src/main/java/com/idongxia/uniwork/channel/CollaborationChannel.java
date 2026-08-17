package com.idongxia.uniwork.channel;

import com.idongxia.uniwork.SendResult;
import com.idongxia.uniwork.UniWorkUser;

/**
 * 企业协作平台共同具备的卡片、登录和用户能力。
 * Shared card, login, and user capabilities for enterprise collaboration platforms.
 */
public interface CollaborationChannel extends UniWorkChannel {

    /**
     * 发送一张带跳转链接的基础卡片消息。
     * Sends a basic card message containing one destination URL.
     *
     * @param receiver 接收人平台账号；receiver account on the target platform
     * @param title 卡片标题；card title
     * @param content 卡片正文；card body
     * @param url 点击卡片后跳转的地址；destination URL
     * @return 平台受理结果；platform acceptance result
     */
    SendResult sendCard(
            String receiver,
            String title,
            String content,
            String url);

    /**
     * 生成平台登录授权地址；使用前需在配置中填写回调地址。
     * Builds the platform authorization URL; a redirect URI must be configured first.
     */
    String loginUrl();

    /**
     * 使用平台回调的授权码完成登录并返回用户信息。
     * Exchanges an authorization code and returns the logged-in user.
     */
    UniWorkUser login(String code);

    /**
     * 按平台用户 ID 查询用户信息。
     * Looks up a user by the platform user ID.
     */
    UniWorkUser getUser(String userId);
}
