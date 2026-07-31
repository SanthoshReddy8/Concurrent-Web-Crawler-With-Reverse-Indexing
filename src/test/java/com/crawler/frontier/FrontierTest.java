package com.crawler.frontier;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierTest {

    @Test
    void deduplicatesUrls() {
        Frontier frontier = new Frontier();

        assertTrue(frontier.offer(new CrawlTask("https://example.com/a", 0)));
        assertFalse(frontier.offer(new CrawlTask("https://example.com/a", 1)));
        assertTrue(frontier.offer(new CrawlTask("https://example.com/b", 0)));

        assertTrue(frontier.queuedCount() >= 2);
        assertTrue(frontier.visitedCount() >= 2);
    }

    @Test
    void deduplicatesConcurrentSubmissions() throws Exception {
        Frontier frontier = new Frontier();
        var executor = Executors.newFixedThreadPool(8);
        try {
            for (int i = 0; i < 100; i++) {
                executor.submit(() -> frontier.offer(new CrawlTask("https://example.com/shared", 0)));
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertTrue(frontier.queuedCount() == 1);
        assertTrue(frontier.visitedCount() == 1);
    }
}
