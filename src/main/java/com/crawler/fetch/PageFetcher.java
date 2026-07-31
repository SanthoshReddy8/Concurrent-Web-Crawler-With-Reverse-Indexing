package com.crawler.fetch;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Downloads pages using the standard Java HTTP client.
 */
public class PageFetcher implements AutoCloseable {

    public static final String DEFAULT_USER_AGENT = "ConcurrentCrawler/1.0 (+educational-project)";
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(200);

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final String userAgent;
    private final int maxAttempts;
    private final Duration initialBackoff;

    public PageFetcher(Duration timeout) {
        this(timeout, DEFAULT_USER_AGENT);
    }

    public PageFetcher(Duration timeout, String userAgent) {
        this(timeout, userAgent, DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_BACKOFF);
    }

    public PageFetcher(Duration timeout, String userAgent, int maxAttempts, Duration initialBackoff) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.requestTimeout = timeout;
        this.userAgent = userAgent;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String userAgent() {
        return userAgent;
    }

    public Optional<FetchedPage> fetch(String url) {
        return fetchInternal(url, true);
    }

    /**
     * Fetches any successful response (used for robots.txt and bronze storage).
     */
    public Optional<FetchedPage> fetchAny(String url) {
        return fetchInternal(url, false);
    }

    private Optional<FetchedPage> fetchInternal(String url, boolean htmlOnly) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .header("User-Agent", userAgent)
                .header("Accept", htmlOnly ? "text/html,application/xhtml+xml" : "*/*")
                .GET()
                .build();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (isRetryable(status) && attempt < maxAttempts) {
                    backoff(attempt);
                    continue;
                }
                if (status < 200 || status >= 300) {
                    return Optional.empty();
                }

                String contentType = response.headers().firstValue("Content-Type").orElse("");
                if (htmlOnly && !isHtml(contentType)) {
                    return Optional.empty();
                }

                return Optional.of(new FetchedPage(
                        url, response.body(), status, contentType, toHeaderMap(response.headers())
                ));
            } catch (IOException e) {
                if (attempt < maxAttempts) {
                    try {
                        backoff(attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return Optional.empty();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    private void backoff(int attempt) throws InterruptedException {
        long multiplier = 1L << Math.min(attempt - 1, 10);
        Thread.sleep(Math.multiplyExact(initialBackoff.toMillis(), multiplier));
    }

    private static boolean isHtml(String contentType) {
        return contentType.contains("text/html") || contentType.contains("application/xhtml");
    }

    private static Map<String, String> toHeaderMap(HttpHeaders headers) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.map().entrySet()) {
            map.put(entry.getKey(), String.join(", ", entry.getValue()));
        }
        return Map.copyOf(map);
    }

    @Override
    public void close() {
        // HttpClient has no explicit close in Java 17.
    }

    public record FetchedPage(
            String url,
            String html,
            int statusCode,
            String contentType,
            Map<String, String> headers
    ) {
    }
}
