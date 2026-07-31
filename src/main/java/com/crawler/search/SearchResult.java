package com.crawler.search;

/**
 * A ranked search hit for a single URL.
 */
public record SearchResult(String url, double score, int matchedTerms, String title, String snippet) {
}
