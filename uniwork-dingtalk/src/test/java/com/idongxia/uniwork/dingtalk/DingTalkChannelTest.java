package com.idongxia.uniwork.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.idongxia.uniwork.SendResult;
import com.idongxia.uniwork.UniWorkUser;
import com.idongxia.uniwork.channel.DingTalkChannel;
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

class DingTalkChannelTest {

    @Test
    void sendsTextAndActionCardWithOneCachedToken() throws Exception {
        MockDingTalk server = new MockDingTalk();
        server.start();
        try {
            DingTalkChannel channel = createChannel(server.baseUrl());

            SendResult text = channel.sendContent("user-1", "待办提醒");
            SendResult card = channel.sendCard(
                    "user-2",
                    "合同审批",
                    "请及时处理",
                    "https://example.com/contracts/1");

            assertEquals("dingtalk", text.getPlatform());
            assertEquals("101", text.getMessageId());
            assertEquals("102", card.getMessageId());
            assertEquals(1, server.appTokenCalls.get());
            assertEquals(2, server.messages.size());
            assertEquals("text", server.messages.get(0).path("msg").path("msgtype").asText());
            assertEquals(
                    "action_card",
                    server.messages.get(1).path("msg").path("msgtype").asText());
            assertEquals(
                    "https://example.com/contracts/1",
                    server.messages.get(1).path("msg").path("action_card")
                            .path("single_url").asText());
        } finally {
            server.close();
        }
    }

    @Test
    void logsInAndReadsAnOrganizationMember() throws Exception {
        MockDingTalk server = new MockDingTalk();
        server.start();
        try {
            DingTalkChannel channel = createChannel(server.baseUrl());

            String loginUrl = channel.loginUrl();
            UniWorkUser loginUser = channel.login("auth-code");
            UniWorkUser member = channel.getUser("staff-100");

            assertTrue(loginUrl.startsWith(server.baseUrl() + "/oauth2/auth?"));
            assertTrue(loginUrl.contains("client_id=ding-app"));
            assertTrue(loginUrl.contains("redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback"));
            assertEquals("union-100", loginUser.getUserId());
            assertEquals("张三", loginUser.getName());
            assertEquals("staff-100", member.getUserId());
            assertEquals("采购部", member.getAttributes().get("title"));
            assertEquals("10,20", member.getAttributes().get("departments"));
        } finally {
            server.close();
        }
    }

    private static DingTalkChannel createChannel(String baseUrl) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("client-id", "ding-app");
        values.put("client-secret", "ding-secret");
        values.put("agent-id", "2000001");
        values.put("redirect-uri", "https://app.example.com/callback");
        values.put("api-base-url", baseUrl);
        values.put("legacy-api-base-url", baseUrl);
        values.put("login-base-url", baseUrl);
        return new DingTalkChannelProvider().create(UniWorkConfig.of(values));
    }

    private static final class MockDingTalk implements AutoCloseable, HttpHandler {
        private final AtomicInteger appTokenCalls = new AtomicInteger();
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
            if ("/v1.0/oauth2/accessToken".equals(path)) {
                appTokenCalls.incrementAndGet();
                respond(exchange, "{\"accessToken\":\"app-token\",\"expireIn\":7200}");
                return;
            }
            if ("/topapi/message/corpconversation/asyncsend_v2".equals(path)) {
                messages.add(JsonSupport.read(read(exchange.getRequestBody())));
                respond(exchange, "{\"errcode\":0,\"errmsg\":\"ok\",\"task_id\":"
                        + (100 + messages.size()) + ",\"request_id\":\"req-1\"}");
                return;
            }
            if ("/v1.0/oauth2/userAccessToken".equals(path)) {
                respond(exchange, "{\"accessToken\":\"user-token\",\"expireIn\":7200}");
                return;
            }
            if ("/v1.0/contact/users/me".equals(path)) {
                assertEquals("user-token", exchange.getRequestHeaders()
                        .getFirst("x-acs-dingtalk-access-token"));
                respond(exchange, "{\"nick\":\"张三\",\"avatarUrl\":\"https://img.example.com/a.png\","
                        + "\"mobile\":\"13800000000\",\"email\":\"z@example.com\","
                        + "\"openId\":\"open-100\",\"unionId\":\"union-100\"}");
                return;
            }
            if ("/topapi/v2/user/get".equals(path)) {
                respond(exchange, "{\"errcode\":0,\"errmsg\":\"ok\",\"result\":{"
                        + "\"userid\":\"staff-100\",\"name\":\"张三\","
                        + "\"title\":\"采购部\",\"dept_id_list\":[10,20]}}");
                return;
            }
            respond(exchange, 404, "{\"code\":\"NotFound\",\"message\":\"not found\"}");
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
