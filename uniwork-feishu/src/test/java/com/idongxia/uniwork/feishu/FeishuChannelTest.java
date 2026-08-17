package com.idongxia.uniwork.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.idongxia.uniwork.SendResult;
import com.idongxia.uniwork.UniWorkUser;
import com.idongxia.uniwork.channel.FeishuChannel;
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

class FeishuChannelTest {

    @Test
    void sendsTextAndInteractiveCardWithOneCachedToken() throws Exception {
        MockFeishu server = new MockFeishu();
        server.start();
        try {
            FeishuChannel channel = createChannel(server.baseUrl());

            SendResult text = channel.sendContent("user-1", "预算提醒");
            SendResult card = channel.sendCard(
                    "user-2",
                    "预算审批",
                    "请及时审核",
                    "https://example.com/budgets/1");

            assertEquals("feishu", text.getPlatform());
            assertEquals("om-1", text.getMessageId());
            assertEquals("om-2", card.getMessageId());
            assertEquals(1, server.tenantTokenCalls.get());
            assertEquals(2, server.messages.size());
            assertEquals("text", server.messages.get(0).path("msg_type").asText());
            JsonNode textContent = JsonSupport.read(
                    server.messages.get(0).path("content").asText());
            assertEquals("预算提醒", textContent.path("text").asText());
            assertEquals("interactive", server.messages.get(1).path("msg_type").asText());
            JsonNode cardContent = JsonSupport.read(
                    server.messages.get(1).path("content").asText());
            assertEquals(
                    "预算审批",
                    cardContent.path("header").path("title").path("content").asText());
            assertEquals(
                    "https://example.com/budgets/1",
                    cardContent.path("elements").get(1).path("actions").get(0)
                            .path("url").asText());
        } finally {
            server.close();
        }
    }

    @Test
    void logsInAndReadsAContactUser() throws Exception {
        MockFeishu server = new MockFeishu();
        server.start();
        try {
            FeishuChannel channel = createChannel(server.baseUrl());

            String loginUrl = channel.loginUrl();
            UniWorkUser loginUser = channel.login("oauth-code");
            UniWorkUser contact = channel.getUser("staff-100");

            assertTrue(loginUrl.startsWith(server.baseUrl() + "/open-apis/authen/v1/authorize?"));
            assertTrue(loginUrl.contains("client_id=cli-app"));
            assertTrue(loginUrl.contains("redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback"));
            assertEquals("staff-100", loginUser.getUserId());
            assertEquals("张三", loginUser.getName());
            assertEquals("staff-100", contact.getUserId());
            assertEquals("E100", contact.getAttributes().get("employeeNo"));
        } finally {
            server.close();
        }
    }

    private static FeishuChannel createChannel(String baseUrl) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("app-id", "cli-app");
        values.put("app-secret", "feishu-secret");
        values.put("redirect-uri", "https://app.example.com/callback");
        values.put("api-base-url", baseUrl);
        values.put("accounts-base-url", baseUrl);
        return new FeishuChannelProvider().create(UniWorkConfig.of(values));
    }

    private static final class MockFeishu implements AutoCloseable, HttpHandler {
        private final AtomicInteger tenantTokenCalls = new AtomicInteger();
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
            if ("/open-apis/auth/v3/tenant_access_token/internal".equals(path)) {
                tenantTokenCalls.incrementAndGet();
                respond(exchange, "{\"code\":0,\"msg\":\"ok\","
                        + "\"tenant_access_token\":\"tenant-token\",\"expire\":7200}");
                return;
            }
            if ("/open-apis/im/v1/messages".equals(path)) {
                assertEquals("Bearer tenant-token", exchange.getRequestHeaders()
                        .getFirst("Authorization"));
                messages.add(JsonSupport.read(read(exchange.getRequestBody())));
                respond(exchange, "{\"code\":0,\"msg\":\"success\",\"data\":{"
                        + "\"message_id\":\"om-" + messages.size() + "\"}}");
                return;
            }
            if ("/open-apis/authen/v2/oauth/token".equals(path)) {
                respond(exchange, "{\"access_token\":\"user-token\",\"expires_in\":7200}");
                return;
            }
            if ("/open-apis/authen/v1/user_info".equals(path)) {
                assertEquals("Bearer user-token", exchange.getRequestHeaders()
                        .getFirst("Authorization"));
                respond(exchange, "{\"code\":0,\"msg\":\"success\",\"data\":{"
                        + "\"name\":\"张三\",\"user_id\":\"staff-100\","
                        + "\"open_id\":\"ou-100\",\"union_id\":\"on-100\","
                        + "\"email\":\"z@example.com\",\"mobile\":\"13800000000\"}}");
                return;
            }
            if (path.startsWith("/open-apis/contact/v3/users/")) {
                respond(exchange, "{\"code\":0,\"msg\":\"success\",\"data\":{\"user\":{"
                        + "\"name\":\"张三\",\"user_id\":\"staff-100\","
                        + "\"employee_no\":\"E100\",\"avatar\":{"
                        + "\"avatar_origin\":\"https://img.example.com/a.png\"}}}}");
                return;
            }
            respond(exchange, 404, "{\"code\":404,\"msg\":\"not found\"}");
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
