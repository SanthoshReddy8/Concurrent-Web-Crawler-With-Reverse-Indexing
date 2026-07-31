package com.crawler.index;

import com.crawler.search.SearchEngine;
import com.crawler.search.SearchResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RedisInvertedIndexIT {

    @Test
    public void redisIndexAndSearch() {
        try (GenericContainer<?> redis = new GenericContainer<>("redis:7.0.11").withExposedPorts(6379)) {
            redis.start();
            String host = redis.getHost();
            Integer port = redis.getMappedPort(6379);

            try (RedisInvertedIndex index = new RedisInvertedIndex(host, port, null)) {
                index.clear();

                index.indexDocument("http://a", Map.of("hello", 1, "world", 2));
                index.indexDocument("http://b", Map.of("hello", 3));

                SearchEngine se = new SearchEngine(index);
                List<SearchResult> results = se.search("hello");

                assertFalse(results.isEmpty(), "Expected results for 'hello'");
                assertEquals("http://b", results.get(0).url(), "Document with higher term freq should rank first");
            }
        }
    }
}
