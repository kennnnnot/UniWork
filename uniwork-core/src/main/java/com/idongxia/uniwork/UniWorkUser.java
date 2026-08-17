package com.idongxia.uniwork;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Framework-neutral user information returned by collaboration channels.
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

    public static Builder builder(String platform, String userId) {
        return new Builder(platform, userId);
    }

    public String getPlatform() {
        return platform;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getEmail() {
        return email;
    }

    public String getMobile() {
        return mobile;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

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

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder avatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder mobile(String mobile) {
            this.mobile = mobile;
            return this;
        }

        public Builder attribute(String name, String value) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("attribute name must not be blank");
            }
            if (value != null) {
                attributes.put(name, value);
            }
            return this;
        }

        public UniWorkUser build() {
            return new UniWorkUser(this);
        }
    }
}
