package com.crawler.frontier;

import java.util.Optional;

public interface CrawlFrontier extends AutoCloseable {

    boolean offer(CrawlTask task);

    Optional<CrawlTask> poll(long timeoutMs) throws InterruptedException;

    void taskFinished(CrawlTask task);

    boolean isIdle();

    int queuedCount();

    int visitedCount();

    @Override
    void close();
}