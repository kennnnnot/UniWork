package com.idongxia.uniwork.example.hospitaloa;

import com.idongxia.uniwork.SendResult;
import com.idongxia.uniwork.UniWork;
import com.idongxia.uniwork.config.UniWorkConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HospitalOaChannelTest {

    @Test
    void isDiscoveredFromConfiguration() {
        UniWork uniWork = UniWork.load();
        try {
            assertTrue(uniWork.hasPlatform(HospitalOaChannel.class));
        } finally {
            uniWork.close();
        }
    }

    @Test
    void postsAConcreteCustomChannelPayload() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/messages", new CapturingHandler(requestBody));
        server.start();
        try {
            Map<String, String> values = new LinkedHashMap<String, String>();
            values.put("endpoint", "http://127.0.0.1:"
                    + server.getAddress().getPort() + "/messages");
            values.put("app-id", "test-app");
            values.put("secret", "test-secret");
            values.put("default-title", "测试通知");

            HospitalOaChannel channel = new HospitalOaChannelProvider()
                    .create(UniWorkConfig.of(values));
            SendResult result = channel.sendContent("EMP10086", "审批提醒");

            assertEquals("hospital-oa", result.getPlatform());
            assertEquals("msg-100", result.getMessageId());
            assertEquals("req-100", result.getRequestId());
            assertEquals(
                    "{\"receiver\":\"EMP10086\",\"title\":\"测试通知\",\"content\":\"审批提醒\"}",
                    requestBody.get());
        } finally {
            server.stop(0);
        }
    }

    private static final class CapturingHandler implements HttpHandler {

        private final AtomicReference<String> requestBody;

        private CapturingHandler(AtomicReference<String> requestBody) {
            this.requestBody = requestBody;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestBody.set(read(exchange.getRequestBody()));
            exchange.getResponseHeaders().add("X-Message-Id", "msg-100");
            exchange.getResponseHeaders().add("X-Request-Id", "req-100");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        }

        private static String read(InputStream input) throws IOException {
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[512];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                input.close();
            }
        }
    }
}
