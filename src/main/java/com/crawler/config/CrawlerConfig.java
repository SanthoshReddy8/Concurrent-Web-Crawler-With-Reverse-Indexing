package com.crawler.config;

import com.crawler.fetch.PageFetcher;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Immutable configuration for the crawler, pipeline, and search engine.
 */
public record CrawlerConfig(
        String seedUrl,
        int maxDepth,
        int workerCount,
        int maxPages,
        Duration perDomainDelay,
        Duration httpTimeout,
        String allowedDomain,
        String redisHost,
        int redisPort,
        String redisPassword,
        String indexExportPath,
        Path dataRoot,
        String userAgent,
        boolean runPipelineAfterCrawl
) {
    public static CrawlerConfig defaults(String seedUrl) {
        URI uri = URI.create(seedUrl);
        String domain = uri.getHost() != null ? uri.getHost() : "";

        return new CrawlerConfig(
                seedUrl,
                3,
                4,
                100,
                Duration.ofMillis(1000),
                Duration.ofSeconds(10),
                domain,
                "localhost",
                6379,
                null,
                "index-export.json",
                Path.of("data"),
                PageFetcher.DEFAULT_USER_AGENT,
                true
        );
    }

    public CrawlerConfig withWorkers(int workers) {
        return new CrawlerConfig(
                seedUrl, maxDepth, workers, maxPages, perDomainDelay,
                httpTimeout, allowedDomain, redisHost, redisPort,
                redisPassword, indexExportPath, dataRoot, userAgent, runPipelineAfterCrawl
        );
    }

    public CrawlerConfig withMaxDepth(int depth) {
        return new CrawlerConfig(
                seedUrl, depth, workerCount, maxPages, perDomainDelay,
                httpTimeout, allowedDomain, redisHost, redisPort,
                redisPassword, indexExportPath, dataRoot, userAgent, runPipelineAfterCrawl
        );
    }

    public CrawlerConfig withMaxPages(int pages) {
        return new CrawlerConfig(
                seedUrl, maxDepth, workerCount, pages, perDomainDelay,
                httpTimeout, allowedDomain, redisHost, redisPort,
                redisPassword, indexExportPath, dataRoot, userAgent, runPipelineAfterCrawl
        );
    }
}
