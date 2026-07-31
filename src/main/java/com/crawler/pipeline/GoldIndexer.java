package com.crawler.pipeline;

import com.crawler.index.InvertedIndex;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Reads Silver JSON documents and populates the Redis inverted index (Gold layer).
 */
public class GoldIndexer {

    private static final Gson GSON = PipelineJson.create(false);

    private final Path silverDir;
    private final InvertedIndex index;

    public GoldIndexer(Path dataRoot, InvertedIndex index) {
        this.silverDir = dataRoot.resolve("silver");
        this.index = index;
    }

    public int indexAll() throws IOException {
        if (!Files.isDirectory(silverDir)) {
            return 0;
        }

        int indexed = 0;
        try (Stream<Path> files = Files.list(silverDir).filter(p -> p.toString().endsWith(".json"))) {
            for (Path file : files.toList()) {
                if (indexOne(file)) {
                    indexed++;
                }
            }
        }
        return indexed;
    }

    public boolean indexOne(Path silverPath) throws IOException {
        if (!Files.exists(silverPath)) {
            return false;
        }

        SilverDocument doc = GSON.fromJson(
                Files.readString(silverPath, StandardCharsets.UTF_8),
                SilverDocument.class
        );
        if (doc == null || doc.termFrequencies() == null || doc.termFrequencies().isEmpty()) {
            return false;
        }

        index.indexDocument(doc.url(), doc.termFrequencies());

        // Generate a simple snippet (fallback to title). Later: generate context around matched terms.
        String snippet = null;
        if (doc.title() != null && !doc.title().isBlank()) {
            snippet = doc.title();
        }

        index.setDocumentMetadata(doc.url(), doc.title(), snippet);
        return true;
    }
}
