package com.idongxia.uniwork.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.idongxia.uniwork.SendResult;
import com.idongxia.uniwork.UniWorkUser;
import com.idongxia.uniwork.channel.WeComChannel;
import com.idongxia.uniwork.config.UniWorkConfig;
import com.idongxia.uniwork.support.JsonSupport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeComChannelTest {

    @Test
    void sendsTextAndCardWithOneCachedToken() throws Exception {
        MockWeCom server = new MockWeCom();
        server.start();
        try {
            WeComChannel channel = createChannel(server.baseUrl());

            SendResult text = channel.sendContent("zhangsan", "审批提醒");
            SendResult card = channel.sendCard(
                    "lisi",
                    "采购审批",
                    "项目等待处理",
                    "https://example.com/tasks/1");

            assertEquals("wecom", text.getPlatform());
            assertEquals("msg-1", text.getMessageId());
            assertEquals("msg-2", card.getMessageId());
            assertEquals(1, server.tokenCalls.get());
            assertEquals(2, server.messages.size());
            assertEquals("text", server.messages.get(0).path("msgtype").asText());
            assertEquals("审批提醒", server.messages.get(0).path("text").path("content").asText());
            assertEquals("textcard", server.messages.get(1).path("msgtype").asText());
            assertEquals(
                    "https://example.com/tasks/1",
                    server.messages.get(1).path("textcard").path("url").asText());
        } finally {
            server.close();
        }
    }

    @Test
    void buildsLoginUrlAndLoadsTheLoggedInMember() throws Exception {
        MockWeCom server = new MockWeCom();
        server.start();
        try {
            WeComChannel channel = createChannel(server.baseUrl());

            String loginUrl = channel.loginUrl();
            UniWorkUser user = channel.login("oauth-code");

            assertTrue(loginUrl.startsWith(server.baseUrl() + "/connect/oauth2/authorize?"));
            assertTrue(loginUrl.contains("redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback"));
            assertTrue(loginUrl.endsWith("#wechat_redirect"));
            assertEquals("wecom", user.getPlatform());
            assertEquals("zhangsan", user.getUserId());
            assertEquals("张三", user.getName());
            assertEquals("13800000000", user.getMobile());
            assertEquals("1,2", user.getAttributes().get("departments"));
        } finally {
            server.close();
        }
    }

    private static WeComChannel createChannel(String baseUrl) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("corp-id", "ww-corp");
        values.put("agent-id", "1000002");
        values.put("secret", "secret");
        values.put("redirect-uri", "https://app.example.com/callback");
        values.put("api-base-url", baseUrl);
        values.put("oauth-base-url", baseUrl);
        return new WeComChannelProvider().create(UniWorkConfig.of(values));
    }

    private static final class MockWeCom implements AutoCloseable, HttpHandler {
        private final AtomicInteger tokenCalls = new AtomicInteger();
        private final List<JsonNode> messages = new ArrayList<JsonNode>();
        private HttpServer server;

        void start() throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", this);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/cgi-bin/gettoken".equals(path)) {
                tokenCalls.incrementAndGet();
                respond(exchange, "{\"errcode\":0,\"access_token\":\"token-1\",\"expires_in\":7200}");
                return;
            }
            if ("/cgi-bin/message/send".equals(path)) {
                messages.add(JsonSupport.read(read(exchange.getRequestBody())));
                respond(exchange, "{\"errcode\":0,\"errmsg\":\"ok\",\"msgid\":\"msg-"
                        + messages.size() + "\"}");
                return;
            }
            if ("/cgi-bin/auth/getuserinfo".equals(path)) {
                respond(exchange, "{\"errcode\":0,\"userid\":\"zhangsan\"}");
                return;
            }
            if ("/cgi-bin/user/get".equals(path)) {
                respond(exchange, "{\"errcode\":0,\"userid\":\"zhangsan\",\"name\":\"张三\","
                        + "\"mobile\":\"13800000000\",\"email\":\"z@example.com\","
                        + "\"avatar\":\"https://img.example.com/a.png\",\"department\":[1,2]}");
                return;
            }
            respond(exchange, 404, "{\"errcode\":404,\"errmsg\":\"not found\"}");
        }

        @Override
        public void close() {
            if (server != null) {
                server.stop(0);
            }
        }

        private static String read(InputStream input) throws IOException {
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[512];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                input.close();
            }
        }

        private static void respond(HttpExchange exchange, String body) throws IOException {
            respond(exchange, 200, body);
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            OutputStream output = exchange.getResponseBody();
            output.write(bytes);
            output.close();
        }
    }
}
