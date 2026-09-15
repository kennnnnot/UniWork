package com.idongxia.uniwork.all;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandaloneJarIT {

    @TempDir
    Path directory;

    @Test
    void packagesJava8ClassesAndPrivateDependenciesWithoutTestConfiguration() throws Exception {
        try (JarFile jar = new JarFile(standaloneJar().toFile())) {
            assertNotNull(jar.getEntry("com/idongxia/uniwork/UniWork.class"));
            assertNotNull(jar.getEntry("com/idongxia/uniwork/internal/jackson/databind/ObjectMapper.class"));
            assertNotNull(jar.getEntry("com/idongxia/uniwork/internal/snakeyaml/Yaml.class"));
            assertNotNull(jar.getEntry("META-INF/LICENSE"));
            assertNotNull(jar.getEntry("META-INF/NOTICE"));
            assertNull(jar.getEntry("uniwork.yml"));
            assertNull(jar.getEntry("uniwork.yaml"));
            assertNull(jar.getEntry("uniwork.properties"));
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                assertFalse(name.startsWith("com/fasterxml/jackson/"), name);
                assertFalse(name.startsWith("org/yaml/snakeyaml/"), name);
                assertFalse(name.startsWith("org/junit/"), name);
                if (name.endsWith(".class")) {
                    try (DataInputStream input = new DataInputStream(jar.getInputStream(entry))) {
                        assertEquals(0xCAFEBABE, input.readInt(), name);
                        input.readUnsignedShort();
                        assertTrue(input.readUnsignedShort() <= 52, "Requires Java newer than 8: " + name);
                    }
                }
            }
        }
    }

    @Test
    void compilesAndSendsThroughAllPlatformsUsingOnlyTheStandaloneJar() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Run this verification with a JDK");
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int result = compiler.run(null, diagnostics, diagnostics,
                "-encoding", "UTF-8", "-source", "8", "-target", "8",
                "-classpath", standaloneJar().toString(), "-d", directory.toString(),
                System.getProperty("uniwork.standalone.source"));
        assertEquals(0, result, new String(diagnostics.toByteArray(), StandardCharsets.UTF_8));

        Map<String, String> responses = responses();
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger chineseMessages = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = read(exchange.getRequestBody());
            if (body.contains("审批提醒") || body.contains("项目等待处理")) {
                chineseMessages.incrementAndGet();
            }
            String response = responses.get(exchange.getRequestURI().getPath());
            requests.incrementAndGet();
            respond(exchange, response == null ? 404 : 200, response == null ? "{}" : response);
        });
        server.start();
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        // Parent has only JDK classes; Maven's module classes and dependencies cannot hide missing JAR contents.
        ClassLoader jdkLoader = ClassLoader.getSystemClassLoader().getParent();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{
                directory.toUri().toURL(), standaloneJar().toUri().toURL()}, jdkLoader)) {
            writeConfiguration("http://127.0.0.1:" + server.getAddress().getPort());
            Thread.currentThread().setContextClassLoader(loader);
            assertThrows(ClassNotFoundException.class,
                    () -> loader.loadClass("com.fasterxml.jackson.databind.ObjectMapper"));
            assertThrows(ClassNotFoundException.class,
                    () -> loader.loadClass("org.yaml.snakeyaml.Yaml"));
            assertEquals(standaloneJar().toUri().toURL(), loader.loadClass("com.idongxia.uniwork.UniWork")
                    .getProtectionDomain().getCodeSource().getLocation());
            loader.loadClass("StandaloneUsage").getMethod("run").invoke(null);
            assertEquals(9, requests.get(), "Three token requests and six message requests");
            assertEquals(6, chineseMessages.get(), "All text and card payloads should preserve Chinese content");
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
            server.stop(0);
        }
    }

    private void writeConfiguration(String baseUrl) throws IOException {
        String yaml = "uniwork:\n"
                + "  wecom:\n"
                + "    corp-id: ww-test\n    agent-id: 1000001\n    secret: test-secret\n"
                + "    api-base-url: " + baseUrl + "\n"
                + "    redirect-uri: https://example.com/callback/wecom\n"
                + "  dingtalk:\n"
                + "    client-id: ding-test\n    client-secret: test-secret\n    agent-id: 2000001\n"
                + "    api-base-url: " + baseUrl + "\n"
                + "    legacy-api-base-url: " + baseUrl + "\n"
                + "    redirect-uri: https://example.com/callback/dingtalk\n"
                + "  feishu:\n"
                + "    app-id: cli-test\n    app-secret: test-secret\n"
                + "    api-base-url: " + baseUrl + "\n"
                + "    redirect-uri: https://example.com/callback/feishu\n";
        Files.write(directory.resolve("uniwork.yml"), yaml.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> responses() {
        Map<String, String> responses = new HashMap<String, String>();
        responses.put("/cgi-bin/gettoken",
                "{\"errcode\":0,\"access_token\":\"wecom-token\",\"expires_in\":7200}");
        responses.put("/cgi-bin/message/send", "{\"errcode\":0,\"msgid\":\"wecom-message\"}");
        responses.put("/v1.0/oauth2/accessToken", "{\"accessToken\":\"dingtalk-token\",\"expireIn\":7200}");
        responses.put("/topapi/message/corpconversation/asyncsend_v2", "{\"errcode\":0,\"task_id\":101}");
        responses.put("/open-apis/auth/v3/tenant_access_token/internal",
                "{\"code\":0,\"tenant_access_token\":\"feishu-token\",\"expire\":7200}");
        responses.put("/open-apis/im/v1/messages",
                "{\"code\":0,\"data\":{\"message_id\":\"feishu-message\"}}");
        return Collections.unmodifiableMap(responses);
    }

    private static String read(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } finally {
            exchange.close();
        }
    }

    private static Path standaloneJar() {
        return Paths.get(System.getProperty("uniwork.standalone.jar"));
    }
}
