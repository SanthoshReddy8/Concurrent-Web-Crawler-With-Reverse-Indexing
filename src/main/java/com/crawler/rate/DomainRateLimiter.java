package com.crawler.rate;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces a minimum delay between consecutive requests to the same domain.
 */
public class DomainRateLimiter {

    private final Duration minDelay;
    private final Map<String, Long> lastAccessMillis = new ConcurrentHashMap<>();

    public DomainRateLimiter(Duration minDelay) {
        this.minDelay = minDelay;
    }

    public void acquire(String url) throws InterruptedException {
        String domain = extractDomain(url);
        if (domain.isEmpty()) {
            return;
        }

        long delayMs = minDelay.toMillis();
        while (true) {
            long now = System.currentTimeMillis();
            Long last = lastAccessMillis.get(domain);
            if (last == null) {
                if (lastAccessMillis.putIfAbsent(domain, now) == null) {
                    return;
                }
                continue;
            }

            long elapsed = now - last;
            if (elapsed >= delayMs) {
                if (lastAccessMillis.replace(domain, last, now)) {
                    return;
                }
                continue;
            }

            Thread.sleep(delayMs - elapsed);
        }
    }

    private static String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() != null ? uri.getHost().toLowerCase() : "";
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
