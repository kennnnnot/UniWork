package com.idongxia.uniwork.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlatformSupportTest {

    @Test
    void encodesQueryComponentsWithoutFormStyleSpaces() {
        String url = UrlSupport.query(
                "https://example.com/oauth",
                "redirect_uri", "https://app.example.com/callback?a=1",
                "scope", "openid corpid");

        assertEquals(
                "https://example.com/oauth?redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback%3Fa%3D1"
                        + "&scope=openid%20corpid",
                url);
    }

    @Test
    void preservesNonJsonErrorResponses() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/failure", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] body = "bad gateway".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(502, body.length);
                OutputStream output = exchange.getResponseBody();
                output.write(body);
                output.close();
            }
        });
        server.start();
        try {
            HttpJsonResponse response = new HttpJsonClient(1000, 1000).get(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/failure");

            assertFalse(response.isSuccessful());
            assertEquals(502, response.getStatusCode());
            assertEquals("bad gateway", response.getBodyText());
        } finally {
            server.stop(0);
        }
    }
}
