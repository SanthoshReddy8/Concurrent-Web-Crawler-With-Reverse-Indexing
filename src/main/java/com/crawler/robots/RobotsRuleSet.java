package com.crawler.robots;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Parsed robots.txt rule set for a single user-agent group.
 */
public final class RobotsRuleSet {

    private final List<PathRule> rules;
    private final boolean allowAll;

    private RobotsRuleSet(List<PathRule> rules, boolean allowAll) {
        this.rules = List.copyOf(rules);
        this.allowAll = allowAll;
    }

    public static RobotsRuleSet allowAll() {
        return new RobotsRuleSet(List.of(), true);
    }

    public static RobotsRuleSet fromRules(List<PathRule> rules) {
        return new RobotsRuleSet(rules, rules.isEmpty());
    }

    /**
     * Returns true when crawling the given URL path is permitted.
     * Uses longest matching rule; Allow overrides Disallow when longer.
     */
    public boolean isAllowed(String urlPath) {
        if (allowAll) {
            return true;
        }

        String path = normalizePath(urlPath);
        PathRule bestMatch = null;

        for (PathRule rule : rules) {
            if (matches(rule.pattern(), path)) {
                if (bestMatch == null || rule.pattern().length() > bestMatch.pattern().length()) {
                    bestMatch = rule;
                } else if (rule.pattern().length() == bestMatch.pattern().length()
                        && rule.allow()
                        && !bestMatch.allow()) {
                    bestMatch = rule;
                }
            }
        }

        if (bestMatch == null) {
            return true;
        }
        return bestMatch.allow();
    }

    static boolean matches(String pattern, String path) {
        if (pattern.isEmpty()) {
            return true;
        }

        boolean endAnchor = pattern.endsWith("$");
        String expression = endAnchor ? pattern.substring(0, pattern.length() - 1) : pattern;

        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if ("\\.[]{}()+-^?|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }

        if (endAnchor) {
            regex.append('$');
        } else {
            regex.append(".*");
        }

        return path.matches(regex.toString());
    }

    static String normalizePath(String urlPath) {
        if (urlPath == null || urlPath.isBlank()) {
            return "/";
        }
        return urlPath.startsWith("/") ? urlPath : "/" + urlPath;
    }

    public record PathRule(String pattern, boolean allow) {
    }

    /**
     * Parses robots.txt content and selects rules for the preferred user-agent.
     */
    public static RobotsRuleSet parse(String content, String preferredUserAgent) {
        if (content == null || content.isBlank()) {
            return allowAll();
        }

        String[] lines = content.split("\\r?\\n");
        List<Group> groups = new ArrayList<>();
        Group current = null;

        for (String rawLine : lines) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }

            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }

            String directive = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();

            switch (directive) {
                case "user-agent" -> {
                    if (current != null) {
                        groups.add(current);
                    }
                    current = new Group(value.toLowerCase(Locale.ROOT));
                }
                case "disallow" -> {
                    if (current != null) {
                        current.rules.add(new PathRule(value, false));
                    }
                }
                case "allow" -> {
                    if (current != null) {
                        current.rules.add(new PathRule(value, true));
                    }
                }
                default -> {
                    // Ignore crawl-delay, sitemap, etc. for path matching.
                }
            }
        }
        if (current != null) {
            groups.add(current);
        }

        Group selected = selectGroup(groups, preferredUserAgent.toLowerCase(Locale.ROOT));
        if (selected == null || selected.rules.isEmpty()) {
            return allowAll();
        }

        selected.rules.sort(Comparator.comparing(PathRule::pattern));
        return fromRules(selected.rules);
    }

    private static Group selectGroup(List<Group> groups, String preferredUserAgent) {
        Group wildcard = null;
        Group exact = null;
        Group prefix = null;

        for (Group group : groups) {
            if ("*".equals(group.agent)) {
                wildcard = group;
            } else if (group.agent.equals(preferredUserAgent)) {
                exact = group;
            } else if (preferredUserAgent.startsWith(group.agent)) {
                if (prefix == null || group.agent.length() > prefix.agent.length()) {
                    prefix = group;
                }
            }
        }

        if (exact != null) {
            return exact;
        }
        if (prefix != null) {
            return prefix;
        }
        return wildcard;
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }

    private static final class Group {
        private final String agent;
        private final List<PathRule> rules = new ArrayList<>();

        private Group(String agent) {
            this.agent = agent;
        }
    }
}
