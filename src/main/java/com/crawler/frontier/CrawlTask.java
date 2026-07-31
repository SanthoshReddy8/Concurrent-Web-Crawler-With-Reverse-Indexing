package com.crawler.frontier;

/**
 * A URL scheduled for crawling together with its depth from the seed.
 */
public record CrawlTask(String url, int depth) {
}
