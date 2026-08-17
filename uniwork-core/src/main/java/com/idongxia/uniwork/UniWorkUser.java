package com.idongxia.uniwork;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 协作平台返回的框架无关用户信息，公共字段之外的数据放入 {@code attributes}。
 * Framework-neutral collaboration user; platform-specific fields are stored in {@code attributes}.
 */
public final class UniWorkUser {

    private final String platform;
    private final String userId;
    private final String name;
    private final String avatarUrl;
    private final String email;
    private final String mobile;
    private final Map<String, String> attributes;

    private UniWorkUser(Builder builder) {
        this.platform = requireText(builder.platform, "platform");
        this.userId = requireText(builder.userId, "userId");
        this.name = builder.name;
        this.avatarUrl = builder.avatarUrl;
        this.email = builder.email;
        this.mobile = builder.mobile;
        this.attributes = Collections.unmodifiableMap(
                new LinkedHashMap<String, String>(builder.attributes));
    }

    /** 创建用户构建器。Creates a user builder. */
    public static Builder builder(String platform, String userId) {
        return new Builder(platform, userId);
    }

    /** 返回平台标识。Returns the platform identifier. */
    public String getPlatform() {
        return platform;
    }

    /** 返回该平台下的用户标识。Returns the user identifier on the platform. */
    public String getUserId() {
        return userId;
    }

    /** 返回用户显示名称。Returns the display name. */
    public String getName() {
        return name;
    }

    /** 返回头像地址。Returns the avatar URL. */
    public String getAvatarUrl() {
        return avatarUrl;
    }

    /** 返回邮箱地址。Returns the email address. */
    public String getEmail() {
        return email;
    }

    /** 返回手机号码。Returns the mobile number. */
    public String getMobile() {
        return mobile;
    }

    /** 返回只读的平台扩展属性。Returns read-only platform-specific attributes. */
    public Map<String, String> getAttributes() {
        return attributes;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    /** 用户信息构建器。Builder for {@link UniWorkUser}. */
    public static final class Builder {

        private final String platform;
        private final String userId;
        private final Map<String, String> attributes = new LinkedHashMap<String, String>();
        private String name;
        private String avatarUrl;
        private String email;
        private String mobile;

        private Builder(String platform, String userId) {
            this.platform = platform;
            this.userId = userId;
        }

        /** 设置显示名称。Sets the display name. */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /** 设置头像地址。Sets the avatar URL. */
        public Builder avatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
            return this;
        }

        /** 设置邮箱地址。Sets the email address. */
        public Builder email(String email) {
            this.email = email;
            return this;
        }

        /** 设置手机号码。Sets the mobile number. */
        public Builder mobile(String mobile) {
            this.mobile = mobile;
            return this;
        }

        /** 添加一个平台扩展属性。Adds a platform-specific attribute. */
        public Builder attribute(String name, String value) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("attribute name must not be blank");
            }
            if (value != null) {
                attributes.put(name, value);
            }
            return this;
        }

        /** 创建不可变用户信息。Builds an immutable user. */
        public UniWorkUser build() {
            return new UniWorkUser(this);
        }
    }
}
