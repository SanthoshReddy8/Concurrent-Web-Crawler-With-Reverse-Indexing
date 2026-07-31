package com.crawler.pipeline;

import java.time.Instant;
import java.util.Map;

/**
 * Raw crawl artifact stored in the Bronze layer.
 */
public record BronzeRecord(
        String id,
        String url,
        int statusCode,
        String contentType,
        Instant fetchedAt,
        Map<String, String> headers,
        String htmlPath
) {
}
