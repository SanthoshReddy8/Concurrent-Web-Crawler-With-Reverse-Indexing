package com.crawler.search;

/**
 * Okapi BM25 scoring with standard Lucene-style parameters.
 */
public final class Bm25Scorer {

    public static final double DEFAULT_K1 = 1.2;
    public static final double DEFAULT_B = 0.75;

    private final double k1;
    private final double b;

    public Bm25Scorer() {
        this(DEFAULT_K1, DEFAULT_B);
    }

    public Bm25Scorer(double k1, double b) {
        this.k1 = k1;
        this.b = b;
    }

    /**
     * IDF for a term: log(1 + (N - df + 0.5) / (df + 0.5))
     */
    public double idf(long documentCount, long documentFrequency) {
        if (documentCount <= 0 || documentFrequency <= 0) {
            return 0.0;
        }
        double numerator = documentCount - documentFrequency + 0.5;
        double denominator = documentFrequency + 0.5;
        return Math.log(1.0 + (numerator / denominator));
    }

    /**
     * Term score contribution for a single query term in one document.
     */
    public double termScore(
            long documentCount,
            long documentFrequency,
            int termFrequency,
            int documentLength,
            double averageDocumentLength
    ) {
        if (termFrequency <= 0 || documentFrequency <= 0 || documentLength <= 0) {
            return 0.0;
        }

        double idf = idf(documentCount, documentFrequency);
        if (averageDocumentLength <= 0.0) {
            averageDocumentLength = documentLength;
        }

        double lengthNorm = 1.0 - b + b * (documentLength / averageDocumentLength);
        double tfNumerator = termFrequency * (k1 + 1.0);
        double tfDenominator = termFrequency + k1 * lengthNorm;
        return idf * (tfNumerator / tfDenominator);
    }

    /**
     * Sum of BM25 term scores for a document given per-term frequencies in that document.
     */
    public double scoreDocument(
            long documentCount,
            double averageDocumentLength,
            int documentLength,
            java.util.Map<String, Integer> queryTermFrequenciesInDoc,
            java.util.function.ToLongFunction<String> documentFrequencyLookup
    ) {
        double total = 0.0;
        for (var entry : queryTermFrequenciesInDoc.entrySet()) {
            String term = entry.getKey();
            int tf = entry.getValue();
            long df = documentFrequencyLookup.applyAsLong(term);
            total += termScore(documentCount, df, tf, documentLength, averageDocumentLength);
        }
        return total;
    }
}
