package com.idongxia.uniwork;

import com.idongxia.uniwork.channel.DingTalkChannel;
import com.idongxia.uniwork.channel.FeishuChannel;
import com.idongxia.uniwork.channel.MailChannel;
import com.idongxia.uniwork.channel.SmsChannel;
import com.idongxia.uniwork.channel.UniWorkChannel;
import com.idongxia.uniwork.channel.WeComChannel;
import com.idongxia.uniwork.config.UniWorkConfig;
import com.idongxia.uniwork.config.UniWorkConfigLoader;
import com.idongxia.uniwork.spi.UniWorkChannelProvider;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Main UniWork facade. It loads configured channel providers from the
 * classpath and exposes short, type-safe accessors.
 */
public final class UniWork implements AutoCloseable {

    private final Map<Class<? extends UniWorkChannel>, UniWorkChannel> channels;

    private UniWork(Map<Class<? extends UniWorkChannel>, UniWorkChannel> channels) {
        this.channels = Collections.unmodifiableMap(
                new LinkedHashMap<Class<? extends UniWorkChannel>, UniWorkChannel>(channels));
    }

    public static UniWork load() {
        return load(Thread.currentThread().getContextClassLoader());
    }

    public static UniWork load(ClassLoader classLoader) {
        ClassLoader effectiveClassLoader = effectiveClassLoader(classLoader);
        UniWorkConfig config = UniWorkConfigLoader.load(effectiveClassLoader);
        return create(config, loadProviders(effectiveClassLoader));
    }

    public static UniWork load(String resourceName) {
        ClassLoader classLoader = effectiveClassLoader(
                Thread.currentThread().getContextClassLoader());
        UniWorkConfig config = UniWorkConfigLoader.load(resourceName, classLoader);
        return create(config, loadProviders(classLoader));
    }

    public static UniWork create(
            UniWorkConfig config,
            Iterable<UniWorkChannelProvider<?>> providers) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (providers == null) {
            throw new IllegalArgumentException("providers must not be null");
        }
        Map<Class<? extends UniWorkChannel>, UniWorkChannel> channels =
                new LinkedHashMap<Class<? extends UniWorkChannel>, UniWorkChannel>();
        try {
            for (UniWorkChannelProvider<?> provider : providers) {
                registerConfiguredProvider(config, provider, channels);
            }
        } catch (ServiceConfigurationError e) {
            throw new UniWorkException("加载 UniWork 渠道扩展失败：" + e.getMessage(), e);
        }
        return new UniWork(channels);
    }

    public WeComChannel wecom() {
        return requiredChannel(WeComChannel.class, "企业微信未配置，请检查 uniwork.wecom 配置");
    }

    public DingTalkChannel dingtalk() {
        return requiredChannel(DingTalkChannel.class, "钉钉未配置，请检查 uniwork.dingtalk 配置");
    }

    public FeishuChannel feishu() {
        return requiredChannel(FeishuChannel.class, "飞书未配置，请检查 uniwork.feishu 配置");
    }

    public MailChannel mail() {
        return requiredChannel(MailChannel.class, "邮箱未配置，请检查 uniwork.mail 配置");
    }

    public SmsChannel sms() {
        return requiredChannel(SmsChannel.class, "短信未配置，请检查 uniwork.sms 配置");
    }

    public <T extends UniWorkChannel> T platform(Class<T> channelType) {
        if (channelType == null) {
            throw new IllegalArgumentException("channelType must not be null");
        }
        return requiredChannel(
                channelType,
                "UniWork 平台未配置：" + channelType.getName());
    }

    public boolean hasPlatform(Class<? extends UniWorkChannel> channelType) {
        return channelType != null && channels.containsKey(channelType);
    }

    @Override
    public void close() {
        Set<UniWorkChannel> closed = Collections.newSetFromMap(
                new IdentityHashMap<UniWorkChannel, Boolean>());
        UniWorkException firstFailure = null;
        for (UniWorkChannel channel : channels.values()) {
            if (!closed.add(channel)) {
                continue;
            }
            try {
                channel.close();
            } catch (RuntimeException e) {
                if (firstFailure == null) {
                    firstFailure = new UniWorkException("关闭 UniWork 渠道失败", e);
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private <T extends UniWorkChannel> T requiredChannel(
            Class<T> channelType,
            String message) {
        UniWorkChannel channel = channels.get(channelType);
        if (channel == null) {
            throw new UniWorkException(message);
        }
        return channelType.cast(channel);
    }

    private static <T extends UniWorkChannel> void registerConfiguredProvider(
            UniWorkConfig config,
            UniWorkChannelProvider<T> provider,
            Map<Class<? extends UniWorkChannel>, UniWorkChannel> channels) {
        if (provider == null) {
            return;
        }
        String prefix = provider.configurationPrefix();
        if (prefix == null || prefix.trim().isEmpty()) {
            throw new UniWorkException(
                    "UniWork 渠道扩展未声明配置前缀：" + provider.getClass().getName());
        }
        if (!config.hasPrefix(prefix)) {
            return;
        }
        Class<T> channelType = provider.channelType();
        if (channelType == null) {
            throw new UniWorkException(
                    "UniWork 渠道扩展未声明渠道类型：" + provider.getClass().getName());
        }
        if (channels.containsKey(channelType)) {
            throw new UniWorkException("UniWork 渠道重复注册：" + channelType.getName());
        }
        T channel = provider.create(config.section(prefix));
        if (channel == null) {
            throw new UniWorkException(
                    "UniWork 渠道扩展创建结果不能为空：" + provider.getClass().getName());
        }
        channels.put(channelType, channel);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Iterable<UniWorkChannelProvider<?>> loadProviders(ClassLoader classLoader) {
        // ServiceLoader accepts only the raw provider class because Java erases T.
        // Keep the unavoidable bridge here so the public extension API stays type-safe.
        return (Iterable) ServiceLoader.load(UniWorkChannelProvider.class, classLoader);
    }

    private static ClassLoader effectiveClassLoader(ClassLoader classLoader) {
        if (classLoader != null) {
            return classLoader;
        }
        ClassLoader fallback = UniWork.class.getClassLoader();
        return fallback == null ? ClassLoader.getSystemClassLoader() : fallback;
    }
}
