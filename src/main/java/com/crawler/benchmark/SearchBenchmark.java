package com.crawler.benchmark;

import com.crawler.index.RedisInvertedIndex;
import com.crawler.search.SearchEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SearchBenchmark {

    private SearchBenchmark() {
    }

    public static Report run(RedisInvertedIndex index, List<String> queries, int warmup, int iterations) {
        if (queries.isEmpty() || iterations < 1 || warmup < 0) {
            throw new IllegalArgumentException("queries cannot be empty and iteration counts must be valid");
        }
        SearchEngine engine = new SearchEngine(index);
        for (int i = 0; i < warmup; i++) {
            engine.search(queries.get(i % queries.size()));
        }

        List<Long> latencies = new ArrayList<>(iterations);
        long started = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            long queryStarted = System.nanoTime();
            engine.search(queries.get(i % queries.size()));
            latencies.add(System.nanoTime() - queryStarted);
        }
        long elapsed = System.nanoTime() - started;
        Collections.sort(latencies);

        double seconds = elapsed / 1_000_000_000.0;
        Runtime runtime = Runtime.getRuntime();
        return new Report(
                iterations,
                iterations / Math.max(seconds, 0.000_001),
                percentileMillis(latencies, 0.50),
                percentileMillis(latencies, 0.95),
                percentileMillis(latencies, 0.99),
                index.indexSizeBytes(),
                runtime.totalMemory() - runtime.freeMemory()
        );
    }

    static double percentileMillis(List<Long> sortedNanos, double percentile) {
        int index = Math.min(sortedNanos.size() - 1, (int) Math.ceil(percentile * sortedNanos.size()) - 1);
        return sortedNanos.get(Math.max(index, 0)) / 1_000_000.0;
    }

    public record Report(
            int iterations,
            double queriesPerSecond,
            double p50Millis,
            double p95Millis,
            double p99Millis,
            long indexBytes,
            long jvmUsedMemoryBytes
    ) {
    }
}