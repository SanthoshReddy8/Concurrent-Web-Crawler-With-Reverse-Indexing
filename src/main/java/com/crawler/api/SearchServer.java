package com.crawler.api;

import com.crawler.index.RedisInvertedIndex;
import com.crawler.search.SearchEngine;
import com.google.gson.Gson;
import io.javalin.Javalin;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight HTTP server exposing search and health endpoints.
 */
public class SearchServer implements AutoCloseable {

    private final Javalin app;
    private final RedisInvertedIndex index;
    private final SearchEngine searchEngine;
    private final Gson gson = new Gson();
    private final PrometheusMeterRegistry prometheusRegistry;
    private final Counter searchCounter;
    private final Timer searchTimer;
    private final Logger log = LoggerFactory.getLogger(SearchServer.class);

    public SearchServer(int httpPort, String redisHost, int redisPort) {
        this.index = new RedisInvertedIndex(redisHost, redisPort, null);
        this.searchEngine = new SearchEngine(index);
        this.prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        this.searchCounter = prometheusRegistry.counter("search_requests_total");
        this.searchTimer = prometheusRegistry.timer("search_request_latency_millis");
        Gauge.builder("search_index_documents", index, RedisInvertedIndex::documentCount)
            .register(prometheusRegistry);
        Gauge.builder("search_index_terms", index, RedisInvertedIndex::termCount)
            .register(prometheusRegistry);
        Gauge.builder("search_index_bytes", index, RedisInvertedIndex::indexSizeBytes)
            .register(prometheusRegistry);

        this.app = Javalin.create(config -> {
            config.http.defaultContentType = "application/json";
        });

        app.get("/search", ctx -> {
            String q = ctx.queryParam("q");
            int limit = parseLimit(ctx.queryParam("limit"));
            if (q == null || q.isBlank()) {
                ctx.status(400).json(Map.of("error", "missing q parameter"));
                return;
            }

            searchCounter.increment();
            long start = System.nanoTime();
            var results = searchEngine.search(q, limit);
            long took = System.nanoTime() - start;
            searchTimer.record(took, TimeUnit.NANOSECONDS);

            log.info("search q='{}' limit={} results={} tookMs={}", q, limit, results.size(), took / 1_000_000);
            ctx.result(gson.toJson(results));
        });

        app.get("/health", ctx -> {
            try {
                long docs = index.documentCount();
                ctx.json(Map.of("ok", true, "redisDocs", docs));
            } catch (Exception e) {
                ctx.status(500).json(Map.of("ok", false, "error", e.getMessage()));
            }
        });

        app.get("/stats", ctx -> {
            double avg = index.documentCount() == 0 ? 0.0 : (double) index.getTotalDocumentLength() / Math.max(1, index.documentCount());
            ctx.json(Map.of(
                    "documents", index.documentCount(),
                    "terms", index.termCount(),
                    "averageDocumentLength", avg
            ));
        });

        app.get("/metrics", ctx -> {
            ctx.contentType("text/plain; version=0.0.4; charset=utf-8");
            ctx.result(prometheusRegistry.scrape());
        });

        app.start(httpPort);
    }

    static int parseLimit(String value) {
        if (value == null || value.isBlank()) {
            return 10;
        }
        try {
            return Math.min(100, Math.max(1, Integer.parseInt(value)));
        } catch (NumberFormatException ex) {
            return 10;
        }
    }

    public void await() throws InterruptedException {
        new CountDownLatch(1).await();
    }

    @Override
    public void close() {
        try {
            app.stop();
        } finally {
            index.close();
        }
    }
}
