package com.crawler.robots;

import com.crawler.fetch.PageFetcher;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache of robots.txt rules per domain.
 */
public class RobotsCache {

    private final PageFetcher fetcher;
    private final String userAgent;
    private final ConcurrentHashMap<String, RobotsRuleSet> cache = new ConcurrentHashMap<>();

    public RobotsCache(PageFetcher fetcher, String userAgent) {
        this.fetcher = fetcher;
        this.userAgent = userAgent;
    }

    public boolean isAllowed(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }

            String domain = host.toLowerCase();
            RobotsRuleSet rules = cache.computeIfAbsent(domain, this::loadRules);
            return rules.isAllowed(buildPath(uri));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private RobotsRuleSet loadRules(String domain) {
        String robotsUrl = "https://" + domain + "/robots.txt";
        Optional<PageFetcher.FetchedPage> fetched = fetcher.fetchAny(robotsUrl);
        if (fetched.isEmpty()) {
            robotsUrl = "http://" + domain + "/robots.txt";
            fetched = fetcher.fetchAny(robotsUrl);
        }

        if (fetched.isEmpty()) {
            return RobotsRuleSet.allowAll();
        }

        return RobotsRuleSet.parse(fetched.get().html(), userAgent);
    }

    static String buildPath(URI uri) {
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        String query = uri.getRawQuery();
        if (query != null && !query.isBlank()) {
            path = path + "?" + query;
        }
        return path;
    }
}
