package com.crawler.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Cleansed, structured document stored in the Silver layer.
 */
public record SilverDocument(
        String id,
        String url,
        String title,
        List<String> links,
        Map<String, Integer> termFrequencies,
        int documentLength,
        Instant processedAt,
        String sourceBronzeId
) {
}
