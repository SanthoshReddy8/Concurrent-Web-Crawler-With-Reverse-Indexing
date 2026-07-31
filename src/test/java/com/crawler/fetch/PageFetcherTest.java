package com.crawler.fetch;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageFetcherTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void retriesTransientServerFailures() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/page", exchange -> {
            int status = requests.incrementAndGet() < 3 ? 503 : 200;
            byte[] body = "<html>ok</html>".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        PageFetcher fetcher = new PageFetcher(
                Duration.ofSeconds(2), "test-agent", 3, Duration.ofMillis(1)
        );
        String url = "http://localhost:" + server.getAddress().getPort() + "/page";

        assertTrue(fetcher.fetch(url).isPresent());
        assertEquals(3, requests.get());
    }

    @Test
    void doesNotRetryPermanentClientErrors() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/missing", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();

        PageFetcher fetcher = new PageFetcher(
                Duration.ofSeconds(2), "test-agent", 3, Duration.ofMillis(1)
        );
        String url = "http://localhost:" + server.getAddress().getPort() + "/missing";

        assertTrue(fetcher.fetch(url).isEmpty());
        assertEquals(1, requests.get());
    }
}