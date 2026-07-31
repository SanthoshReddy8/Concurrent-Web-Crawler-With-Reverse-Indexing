package com.crawler.worker;

import com.crawler.config.CrawlerConfig;
import com.crawler.fetch.PageFetcher;
import com.crawler.fetch.PageParser;
import com.crawler.frontier.CrawlFrontier;
import com.crawler.frontier.CrawlTask;
import com.crawler.pipeline.BronzeStore;
import com.crawler.rate.DomainRateLimiter;
import com.crawler.robots.RobotsCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Worker thread that pulls URLs from the frontier, writes Bronze artifacts,
 * and discovers links for frontier expansion.
 */
public class CrawlerWorker implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(CrawlerWorker.class);

    private final CrawlFrontier frontier;
    private final PageFetcher fetcher;
    private final PageParser parser;
    private final BronzeStore bronzeStore;
    private final DomainRateLimiter rateLimiter;
    private final RobotsCache robotsCache;
    private final CrawlerConfig config;
    private final AtomicInteger pagesCrawled;
    private final AtomicInteger crawlLimitReached;

    public CrawlerWorker(
            CrawlFrontier frontier,
            PageFetcher fetcher,
            PageParser parser,
            BronzeStore bronzeStore,
            DomainRateLimiter rateLimiter,
            RobotsCache robotsCache,
            CrawlerConfig config,
            AtomicInteger pagesCrawled,
            AtomicInteger crawlLimitReached
    ) {
        this.frontier = frontier;
        this.fetcher = fetcher;
        this.parser = parser;
        this.bronzeStore = bronzeStore;
        this.rateLimiter = rateLimiter;
        this.robotsCache = robotsCache;
        this.config = config;
        this.pagesCrawled = pagesCrawled;
        this.crawlLimitReached = crawlLimitReached;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (crawlLimitReached.get() > 0) {
                    break;
                }

                var taskOptional = frontier.poll(500);
                if (taskOptional.isEmpty()) {
                    if (frontier.isIdle()) {
                        break;
                    }
                    continue;
                }

                CrawlTask task = taskOptional.get();
                try {
                    processTask(task);
                } finally {
                    frontier.taskFinished(task);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void processTask(CrawlTask task) throws InterruptedException {
        if (crawlLimitReached.get() > 0) {
            return;
        }

        if (!isAllowedDomain(task.url())) {
            return;
        }

        if (!robotsCache.isAllowed(task.url())) {
            return;
        }

        rateLimiter.acquire(task.url());

        var fetched = fetcher.fetch(task.url());
        if (fetched.isEmpty()) {
            return;
        }

        int crawled = pagesCrawled.incrementAndGet();
        if (crawled > config.maxPages()) {
            crawlLimitReached.compareAndSet(0, 1);
            return;
        }

        try {
            bronzeStore.save(fetched.get());
        } catch (Exception e) {
            LOG.error("Failed to persist crawled page {}", task.url(), e);
            return;
        }

        PageParser.ParsedPage parsed = parser.parse(task.url(), fetched.get().html());

        if (task.depth() >= config.maxDepth()) {
            return;
        }

        for (String link : parsed.links()) {
            if (!isAllowedDomain(link)) {
                continue;
            }
            if (!robotsCache.isAllowed(link)) {
                continue;
            }
            frontier.offer(new CrawlTask(link, task.depth() + 1));
        }
    }

    private boolean isAllowedDomain(String url) {
        if (config.allowedDomain() == null || config.allowedDomain().isBlank()) {
            return true;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String allowed = config.allowedDomain().toLowerCase();
            String normalizedHost = host.toLowerCase();
            return normalizedHost.equals(allowed) || normalizedHost.endsWith("." + allowed);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
