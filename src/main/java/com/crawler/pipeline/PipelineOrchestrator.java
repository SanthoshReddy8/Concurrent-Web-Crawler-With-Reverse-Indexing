package com.crawler.pipeline;

import com.crawler.index.InvertedIndex;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Orchestrates Silver and Gold medallion pipeline stages.
 */
public class PipelineOrchestrator {

    private final Path dataRoot;
    private final InvertedIndex index;

    public PipelineOrchestrator(Path dataRoot, InvertedIndex index) {
        this.dataRoot = dataRoot;
        this.index = index;
    }

    public PipelineStats runSilver() throws IOException {
        SilverProcessor processor = new SilverProcessor(dataRoot);
        int processed = processor.processAll();
        return new PipelineStats(processed, 0);
    }

    public PipelineStats runGold() throws IOException {
        GoldIndexer indexer = new GoldIndexer(dataRoot, index);
        int indexed = indexer.indexAll();
        return new PipelineStats(0, indexed);
    }

    public PipelineStats runAll() throws IOException {
        PipelineStats silver = runSilver();
        PipelineStats gold = runGold();
        return new PipelineStats(silver.silverDocuments(), gold.goldDocuments());
    }

    public record PipelineStats(int silverDocuments, int goldDocuments) {
    }
}
