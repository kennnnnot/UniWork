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
 * UniWork 主入口：从 classpath 发现已配置渠道，并提供简短、类型安全的调用方法。
 * Main facade that discovers configured channels from the classpath and exposes short, type-safe accessors.
 */
public final class UniWork implements AutoCloseable {

    private final Map<Class<? extends UniWorkChannel>, UniWorkChannel> channels;

    private UniWork(Map<Class<? extends UniWorkChannel>, UniWorkChannel> channels) {
        this.channels = Collections.unmodifiableMap(
                new LinkedHashMap<Class<? extends UniWorkChannel>, UniWorkChannel>(channels));
    }

    /**
     * 从默认的 {@code uniwork.yml}、{@code uniwork.yaml} 或 {@code uniwork.properties} 加载。
     * Loads from the default {@code uniwork.yml}, {@code uniwork.yaml}, or {@code uniwork.properties} resource.
     *
     * @return 已配置的 UniWork 实例；configured UniWork instance
     */
    public static UniWork load() {
        return load(Thread.currentThread().getContextClassLoader());
    }

    /**
     * 使用指定类加载器读取默认配置并发现扩展。
     * Uses the supplied class loader to read the default configuration and discover extensions.
     */
    public static UniWork load(ClassLoader classLoader) {
        ClassLoader effectiveClassLoader = effectiveClassLoader(classLoader);
        UniWorkConfig config = UniWorkConfigLoader.load(effectiveClassLoader);
        return create(config, loadProviders(effectiveClassLoader));
    }

    /**
     * 从指定的 classpath 配置文件加载。
     * Loads from a named classpath configuration resource.
     *
     * @param resourceName classpath 配置文件名；classpath resource name
     * @return 已配置的 UniWork 实例；configured UniWork instance
     */
    public static UniWork load(String resourceName) {
        ClassLoader classLoader = effectiveClassLoader(
                Thread.currentThread().getContextClassLoader());
        UniWorkConfig config = UniWorkConfigLoader.load(resourceName, classLoader);
        return create(config, loadProviders(classLoader));
    }

    /**
     * 使用内存配置和明确提供的 Provider 创建实例，适合框架集成与测试。
     * Creates an instance from in-memory configuration and explicit providers for integrations and tests.
     */
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

    /** 返回企业微信渠道。Returns the configured WeCom channel. */
    public WeComChannel wecom() {
        return requiredChannel(WeComChannel.class, "企业微信未配置，请检查 uniwork.wecom 配置");
    }

    /** 返回钉钉渠道。Returns the configured DingTalk channel. */
    public DingTalkChannel dingtalk() {
        return requiredChannel(DingTalkChannel.class, "钉钉未配置，请检查 uniwork.dingtalk 配置");
    }

    /** 返回飞书渠道。Returns the configured Feishu channel. */
    public FeishuChannel feishu() {
        return requiredChannel(FeishuChannel.class, "飞书未配置，请检查 uniwork.feishu 配置");
    }

    /** 返回邮箱渠道。Returns the configured email channel. */
    public MailChannel mail() {
        return requiredChannel(MailChannel.class, "邮箱未配置，请检查 uniwork.mail 配置");
    }

    /** 返回短信渠道。Returns the configured SMS channel. */
    public SmsChannel sms() {
        return requiredChannel(SmsChannel.class, "短信未配置，请检查 uniwork.sms 配置");
    }

    /**
     * 按自定义渠道接口类型返回扩展，不使用字符串别名。
     * Returns a custom extension by its channel interface type without string aliases.
     */
    public <T extends UniWorkChannel> T platform(Class<T> channelType) {
        if (channelType == null) {
            throw new IllegalArgumentException("channelType must not be null");
        }
        return requiredChannel(
                channelType,
                "UniWork 平台未配置：" + channelType.getName());
    }

    /** 判断某个渠道接口是否已经配置并加载。Checks whether a channel type is configured and loaded. */
    public boolean hasPlatform(Class<? extends UniWorkChannel> channelType) {
        return channelType != null && channels.containsKey(channelType);
    }

    /** 关闭所有已加载渠道并释放资源。Closes all loaded channels and releases resources. */
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
        // Java 泛型擦除后 ServiceLoader 只能接收原始 Provider 类型，因此把不可避免的转换限制在此处。
        // ServiceLoader accepts only the raw provider class after erasure; keep the bridge here.
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
