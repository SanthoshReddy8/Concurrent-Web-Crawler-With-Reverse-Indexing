package com.crawler.robots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RobotsRuleSetTest {

    @Test
    void longestRuleWinsBetweenAllowAndDisallow() {
        String robots = """
                User-agent: ConcurrentCrawler
                Disallow: /private/
                Allow: /private/public/
                """;
        RobotsRuleSet rules = RobotsRuleSet.parse(robots, "ConcurrentCrawler/1.0");

        assertFalse(rules.isAllowed("/private/secret"));
        assertTrue(rules.isAllowed("/private/public/page"));
        assertTrue(rules.isAllowed("/public/page"));
    }

    @Test
    void wildcardPatternsMatchExtensions() {
        String robots = """
                User-agent: *
                Disallow: /*.pdf$
                """;
        RobotsRuleSet rules = RobotsRuleSet.parse(robots, "ConcurrentCrawler/1.0");

        assertFalse(rules.isAllowed("/docs/report.pdf"));
        assertTrue(rules.isAllowed("/docs/report.html"));
    }

    @Test
    void emptyRobotsAllowsEverything() {
        RobotsRuleSet rules = RobotsRuleSet.parse("", "ConcurrentCrawler/1.0");
        assertTrue(rules.isAllowed("/anything"));
    }
}
