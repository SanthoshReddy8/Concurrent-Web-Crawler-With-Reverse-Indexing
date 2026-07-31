package com.crawler.index;

import java.util.Map;

/**
 * Thread-safe inverted index abstraction with BM25 metadata accessors.
 */
public interface InvertedIndex extends AutoCloseable {

    void indexDocument(String url, Map<String, Integer> termFrequencies);

    Map<String, Integer> getPostings(String term);

    long getDocumentFrequency(String term);

    int getDocumentLength(String url);

    long getTotalDocumentLength();

    void exportToJson(String path);

    long documentCount();

    long termCount();

    default String getDocumentTitle(String url) {
        return null;
    }

    default String getDocumentSnippet(String url) {
        return null;
    }

    default void setDocumentMetadata(String url, String title, String snippet) {
        // optional
    }
}
