package com.crawler.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Bm25ScorerTest {

    private final Bm25Scorer scorer = new Bm25Scorer();

    @Test
    void rareTermsScoreHigherThanCommonTerms() {
        long documentCount = 100;
        int documentLength = 200;
        double avgLength = 200.0;

        double rareScore = scorer.termScore(documentCount, 2, 3, documentLength, avgLength);
        double commonScore = scorer.termScore(documentCount, 80, 3, documentLength, avgLength);

        assertTrue(rareScore > commonScore);
    }

    @Test
    void longerDocumentsDoNotDominateWhenTermFrequencyIsEqual() {
        long documentCount = 10;
        long df = 5;
        double avgLength = 150.0;

        double shortDoc = scorer.termScore(documentCount, df, 4, 100, avgLength);
        double longDoc = scorer.termScore(documentCount, df, 4, 400, avgLength);

        assertTrue(shortDoc > longDoc);
    }
}
