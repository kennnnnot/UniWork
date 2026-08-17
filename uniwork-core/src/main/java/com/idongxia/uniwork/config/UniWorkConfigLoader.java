package com.idongxia.uniwork.config;

import com.idongxia.uniwork.UniWorkException;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Classpath configuration loader used by framework-neutral Java applications. */
public final class UniWorkConfigLoader {

    private static final String[] DEFAULT_RESOURCES = {
            "uniwork.yml", "uniwork.yaml", "uniwork.properties"
    };
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

    private UniWorkConfigLoader() {
    }

    public static UniWorkConfig load(ClassLoader classLoader) {
        ClassLoader effectiveClassLoader = effectiveClassLoader(classLoader);
        for (String resource : DEFAULT_RESOURCES) {
            if (effectiveClassLoader.getResource(resource) != null) {
                return load(resource, effectiveClassLoader);
            }
        }
        throw new UniWorkException(
                "未找到 UniWork 配置文件，请在 classpath 中添加 uniwork.yml、uniwork.yaml 或 uniwork.properties");
    }

    public static UniWorkConfig load(String resourceName, ClassLoader classLoader) {
        if (resourceName == null || resourceName.trim().isEmpty()) {
            throw new IllegalArgumentException("resourceName must not be blank");
        }
        ClassLoader effectiveClassLoader = effectiveClassLoader(classLoader);
        InputStream input = effectiveClassLoader.getResourceAsStream(resourceName);
        if (input == null) {
            throw new UniWorkException("未找到 UniWork 配置文件：" + resourceName);
        }
        try {
            Map<String, String> values;
            if (resourceName.endsWith(".properties")) {
                values = loadProperties(input);
            } else {
                values = loadYaml(input);
            }
            return UniWorkConfig.of(resolvePlaceholders(values));
        } catch (IOException e) {
            throw new UniWorkException("读取 UniWork 配置文件失败：" + resourceName, e);
        } catch (RuntimeException e) {
            if (e instanceof UniWorkException) {
                throw e;
            }
            throw new UniWorkException("解析 UniWork 配置文件失败：" + resourceName, e);
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
                // The original parsing error, if any, is more useful to callers.
            }
        }
    }

    private static Map<String, String> loadProperties(InputStream input) throws IOException {
        Properties properties = new Properties();
        properties.load(input);
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith("uniwork.")) {
                values.put(name.substring("uniwork.".length()), properties.getProperty(name));
            }
        }
        if (values.isEmpty()) {
            throw new UniWorkException("配置文件中没有 uniwork.* 配置项");
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> loadYaml(InputStream input) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        Object document = yaml.load(input);
        if (!(document instanceof Map)) {
            throw new UniWorkException("YAML 根节点必须是对象");
        }
        Object root = ((Map<Object, Object>) document).get("uniwork");
        if (!(root instanceof Map)) {
            throw new UniWorkException("YAML 中缺少 uniwork 根配置");
        }
        Map<String, String> values = new LinkedHashMap<String, String>();
        flatten("", (Map<Object, Object>) root, values);
        return values;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(
            String prefix,
            Map<Object, Object> source,
            Map<String, String> target) {
        for (Map.Entry<Object, Object> entry : source.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = entry.getValue();
            if (value instanceof Map) {
                flatten(path, (Map<Object, Object>) value, target);
            } else if (value != null) {
                target.put(path, String.valueOf(value));
            }
        }
    }

    private static Map<String, String> resolvePlaceholders(Map<String, String> source) {
        Map<String, String> resolved = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            resolved.put(entry.getKey(), resolvePlaceholders(entry.getValue()));
        }
        return resolved;
    }

    private static String resolvePlaceholders(String value) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = System.getProperty(name);
            if (replacement == null) {
                replacement = System.getenv(name);
            }
            if (replacement == null) {
                replacement = matcher.group(2);
            }
            if (replacement == null) {
                throw new UniWorkException("缺少环境变量或系统属性：" + name);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static ClassLoader effectiveClassLoader(ClassLoader classLoader) {
        if (classLoader != null) {
            return classLoader;
        }
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        return contextClassLoader == null
                ? UniWorkConfigLoader.class.getClassLoader()
                : contextClassLoader;
    }
}
