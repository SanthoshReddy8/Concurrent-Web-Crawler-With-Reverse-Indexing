package com.crawler.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchBenchmarkTest {

    @Test
    void calculatesNearestRankPercentiles() {
        List<Long> samples = List.of(1_000_000L, 2_000_000L, 3_000_000L, 4_000_000L);

        assertEquals(2.0, SearchBenchmark.percentileMillis(samples, 0.50));
        assertEquals(4.0, SearchBenchmark.percentileMillis(samples, 0.95));
    }
}