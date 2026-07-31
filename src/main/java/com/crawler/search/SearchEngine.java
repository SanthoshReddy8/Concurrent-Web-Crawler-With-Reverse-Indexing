package com.crawler.search;

import com.crawler.index.InvertedIndex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Queries the inverted index and ranks results using Okapi BM25.
 */
public class SearchEngine {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]+");

    private final InvertedIndex index;
    private final Bm25Scorer scorer;

    public SearchEngine(InvertedIndex index) {
        this(index, new Bm25Scorer());
    }

    public SearchEngine(InvertedIndex index, Bm25Scorer scorer) {
        this.index = index;
        this.scorer = scorer;
    }

    public List<SearchResult> search(String query) {
        return search(query, 10);
    }

    public List<SearchResult> search(String query, int limit) {
        List<String> terms = tokenize(query);
        if (terms.isEmpty()) {
            return List.of();
        }

        long documentCount = index.documentCount();
        if (documentCount == 0) {
            return List.of();
        }

        double averageDocumentLength = averageDocumentLength();
        Map<String, Map<String, Integer>> candidatePostings = new HashMap<>();

        for (String term : terms) {
            Map<String, Integer> postings = index.getPostings(term);
            for (Map.Entry<String, Integer> entry : postings.entrySet()) {
                candidatePostings
                        .computeIfAbsent(entry.getKey(), ignored -> new HashMap<>())
                        .put(term, entry.getValue());
            }
        }

        List<SearchResult> results = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> candidate : candidatePostings.entrySet()) {
            String url = candidate.getKey();
            int documentLength = index.getDocumentLength(url);
            if (documentLength <= 0) {
                continue;
            }

            double score = scorer.scoreDocument(
                    documentCount,
                    averageDocumentLength,
                    documentLength,
                    candidate.getValue(),
                    index::getDocumentFrequency
            );

            int matchedTerms = candidate.getValue().size();
            String title = index.getDocumentTitle(url);
            String snippet = index.getDocumentSnippet(url);
            results.add(new SearchResult(url, score, matchedTerms, title, snippet));
        }

        results.sort(Comparator
                .comparingDouble(SearchResult::score).reversed()
                .thenComparing(Comparator.comparingInt(SearchResult::matchedTerms).reversed()));

        if (results.size() <= limit) {
            return results;
        }
        return results.subList(0, limit);
    }

    private double averageDocumentLength() {
        long total = index.getTotalDocumentLength();
        long count = index.documentCount();
        if (count == 0) {
            return 0.0;
        }
        return (double) total / count;
    }

    private List<String> tokenize(String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        var matcher = TOKEN_PATTERN.matcher(lower);
        Set<String> unique = new HashSet<>();
        List<String> terms = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < 2) {
                continue;
            }
            if (unique.add(token)) {
                terms.add(token);
            }
        }
        return terms;
    }
}
