package com.crawler.frontier;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe URL frontier with a deduplicating visited set.
 */
public class Frontier implements CrawlFrontier {

    private final BlockingQueue<CrawlTask> queue = new LinkedBlockingQueue<>();
    private final Set<String> visited = ConcurrentHashMap.newKeySet();
    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    private volatile boolean closed = false;

    /**
     * Attempts to enqueue a URL if it has not been visited yet.
     *
     * @return true when the URL was newly scheduled
     */
    public boolean offer(CrawlTask task) {
        if (closed) {
            return false;
        }
        if (visited.add(task.url())) {
            queue.offer(task);
            return true;
        }
        return false;
    }

    public Optional<CrawlTask> poll(long timeoutMs) throws InterruptedException {
        CrawlTask task = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (task != null) {
            activeWorkers.incrementAndGet();
        }
        return Optional.ofNullable(task);
    }

    @Override
    public void taskFinished(CrawlTask task) {
        activeWorkers.decrementAndGet();
    }

    public boolean isIdle() {
        return queue.isEmpty() && activeWorkers.get() == 0;
    }

    public int queuedCount() {
        return queue.size();
    }

    public int visitedCount() {
        return visited.size();
    }

    @Override
    public void close() {
        closed = true;
    }
}
