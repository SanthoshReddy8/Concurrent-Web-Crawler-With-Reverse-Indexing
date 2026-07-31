package com.crawler.fetch;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageParserTest {

    @Test
    void extractsLinksAndTerms() {
        PageParser parser = new PageParser();
        String html = """
                <html>
                  <head><title>Python Guide</title></head>
                  <body>
                    <p>Python concurrency is powerful.</p>
                    <a href="/docs/threading">Threading</a>
                    <a href="https://example.com/other">External</a>
                  </body>
                </html>
                """;

        PageParser.ParsedPage page = parser.parse("https://example.com/guide", html);

        assertTrue(page.title().contains("Python"));

        assertTrue(page.links().contains("https://example.com/docs/threading"));
        assertTrue(page.links().contains("https://example.com/other"));

        Map<String, Integer> terms = page.termFrequencies();
        assertTrue(terms.containsKey("python"));
        assertTrue(terms.get("python") >= 1);
        assertTrue(terms.containsKey("concurrency"));
    }

    @Test
    void ignoresVeryShortTokens() {
        PageParser parser = new PageParser();
        PageParser.ParsedPage page = parser.parse(
                "https://example.com",
                "<html><body>a I am ok</body></html>"
        );

        assertFalse(page.termFrequencies().containsKey("a"));
        assertFalse(page.termFrequencies().containsKey("i"));
        assertTrue(page.termFrequencies().containsKey("am"));
    }
}
