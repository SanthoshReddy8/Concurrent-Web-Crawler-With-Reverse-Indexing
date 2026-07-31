package com.crawler.fetch;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses HTML into outgoing links and token frequency counts.
 */
public class PageParser {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]+");

    public ParsedPage parse(String pageUrl, String html) {
        Document document = Jsoup.parse(html, pageUrl);
        List<String> links = extractLinks(pageUrl, document);
        Map<String, Integer> termFrequencies = extractTermFrequencies(document);
        String title = document.title();
        return new ParsedPage(pageUrl, title, links, termFrequencies);
    }

    private List<String> extractLinks(String pageUrl, Document document) {
        List<String> links = new ArrayList<>();
        Elements anchors = document.select("a[href]");
        for (Element anchor : anchors) {
            String href = anchor.absUrl("href");
            if (href.isBlank()) {
                continue;
            }
            if (href.startsWith("http://") || href.startsWith("https://")) {
                links.add(normalizeUrl(href));
            }
        }
        return links;
    }

    private Map<String, Integer> extractTermFrequencies(Document document) {
        String text = document.body() != null ? document.body().text() : document.text();
        String normalized = text.toLowerCase(Locale.ROOT);
        Map<String, Integer> frequencies = new HashMap<>();

        var matcher = TOKEN_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < 2) {
                continue;
            }
            frequencies.merge(token, 1, Integer::sum);
        }
        return frequencies;
    }

    private String normalizeUrl(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            return new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    path,
                    uri.getQuery(),
                    null
            ).toString();
        } catch (Exception e) {
            return url;
        }
    }

    public record ParsedPage(String url, String title, List<String> links, Map<String, Integer> termFrequencies) {
    }
}
