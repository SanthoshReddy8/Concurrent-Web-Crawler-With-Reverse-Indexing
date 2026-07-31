package com.crawler;

import com.crawler.config.CrawlerConfig;
import com.crawler.fetch.PageFetcher;
import com.crawler.fetch.PageParser;
import com.crawler.frontier.CrawlFrontier;
import com.crawler.frontier.CrawlTask;
import com.crawler.frontier.Frontier;
import com.crawler.index.InvertedIndex;
import com.crawler.index.RedisInvertedIndex;
import com.crawler.pipeline.BronzeStore;
import com.crawler.pipeline.PipelineOrchestrator;
import com.crawler.rate.DomainRateLimiter;
import com.crawler.robots.RobotsCache;
import com.crawler.worker.CrawlerWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates crawling (Bronze), optional pipeline (Silver/Gold), and index export.
 */
public class CrawlerEngine implements AutoCloseable {

    private final CrawlerConfig config;
    private final CrawlFrontier frontier;
    private final PageFetcher fetcher;
    private final PageParser parser = new PageParser();
    private final BronzeStore bronzeStore;
    private final InvertedIndex index;
    private final DomainRateLimiter rateLimiter;
    private final RobotsCache robotsCache;
    private final AtomicInteger pagesCrawled = new AtomicInteger(0);
    private final AtomicInteger crawlLimitReached = new AtomicInteger(0);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile ExecutorService workerPool;

    public CrawlerEngine(CrawlerConfig config) {
        this(config, new Frontier());
    }

    public CrawlerEngine(CrawlerConfig config, CrawlFrontier frontier) {
        this.config = config;
        this.frontier = frontier;
        this.fetcher = new PageFetcher(config.httpTimeout(), config.userAgent());
        this.bronzeStore = new BronzeStore(config.dataRoot());
        this.index = new RedisInvertedIndex(
                config.redisHost(),
                config.redisPort(),
                config.redisPassword()
        );
        this.rateLimiter = new DomainRateLimiter(config.perDomainDelay());
        this.robotsCache = new RobotsCache(fetcher, config.userAgent());
    }

    public CrawlStats crawl() throws Exception {
        long started = System.nanoTime();
        frontier.offer(new CrawlTask(config.seedUrl(), 0));

        int workers = Math.max(1, config.workerCount());
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        workerPool = pool;

        for (int i = 0; i < workers; i++) {
            CrawlerWorker worker = new CrawlerWorker(
                    frontier,
                    fetcher,
                    parser,
                    bronzeStore,
                    rateLimiter,
                    robotsCache,
                    config,
                    pagesCrawled,
                    crawlLimitReached
            );
            pool.submit(worker);
        }

        pool.shutdown();
        boolean finished = pool.awaitTermination(30, TimeUnit.MINUTES);
        if (!finished) {
            pool.shutdownNow();
        }

        int silverDocs = 0;
        int goldDocs = 0;
        if (config.runPipelineAfterCrawl()) {
            PipelineOrchestrator pipeline = new PipelineOrchestrator(config.dataRoot(), index);
            PipelineOrchestrator.PipelineStats stats = pipeline.runAll();
            silverDocs = stats.silverDocuments();
            goldDocs = stats.goldDocuments();
        }

        index.exportToJson(config.indexExportPath());

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        Runtime runtime = Runtime.getRuntime();
        return new CrawlStats(
                pagesCrawled.get(),
                frontier.visitedCount(),
                silverDocs,
                goldDocs,
                index.documentCount(),
                index.termCount(),
                bronzeStore.bronzeDir().toString(),
                config.indexExportPath(),
                elapsedMillis,
                pagesCrawled.get() / Math.max(elapsedMillis / 1000.0, 0.001),
                ((RedisInvertedIndex) index).indexSizeBytes(),
                runtime.totalMemory() - runtime.freeMemory()
        );
    }

    public InvertedIndex index() {
        return index;
    }

    public PipelineOrchestrator pipeline() {
        return new PipelineOrchestrator(config.dataRoot(), index);
    }

    @Override
    public void close() throws Exception {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ExecutorService pool = workerPool;
        if (pool != null && !pool.isTerminated()) {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
        frontier.close();
        fetcher.close();
        index.close();
    }

    public record CrawlStats(
            int pagesCrawled,
            int urlsDiscovered,
            int silverDocuments,
            int goldDocuments,
            long indexedDocuments,
            long indexedTerms,
            String bronzePath,
            String exportPath,
            long elapsedMillis,
            double pagesPerSecond,
            long indexBytes,
            long jvmUsedMemoryBytes
    ) {
    }
}
