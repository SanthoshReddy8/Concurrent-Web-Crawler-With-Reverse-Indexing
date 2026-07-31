package com.crawler.frontier;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisFrontierIT {

    @Test
    void sharesDeduplicationAndReturnsLeasesOnShutdown() throws Exception {
        try (GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379)) {
            redis.start();
            String namespace = "test-" + UUID.randomUUID();
            CrawlTask task = new CrawlTask("https://example.com/page", 1);

            RedisFrontier first = new RedisFrontier(
                    redis.getHost(), redis.getMappedPort(6379), namespace, Duration.ofMinutes(1)
            );
            RedisFrontier second = new RedisFrontier(
                    redis.getHost(), redis.getMappedPort(6379), namespace, Duration.ofMinutes(1)
            );
            try {
                assertTrue(first.offer(task));
                assertFalse(second.offer(task));
                assertEquals(task, first.poll(100).orElseThrow());

                first.close();

                assertEquals(task, second.poll(500).orElseThrow());
                second.taskFinished(task);
                assertTrue(second.isIdle());
            } finally {
                second.close();
            }
        }
    }
}
