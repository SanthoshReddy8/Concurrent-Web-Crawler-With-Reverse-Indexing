package com.crawler;

import com.crawler.benchmark.SearchBenchmark;
import com.crawler.config.CrawlerConfig;
import com.crawler.fetch.PageFetcher;
import com.crawler.frontier.CrawlFrontier;
import com.crawler.frontier.Frontier;
import com.crawler.frontier.RedisFrontier;
import com.crawler.index.RedisInvertedIndex;
import com.crawler.pipeline.PipelineOrchestrator;
import com.crawler.search.SearchEngine;
import com.crawler.search.SearchResult;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * CLI entry point for crawling, pipeline stages, and search.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String command = args[0].toLowerCase(Locale.ROOT);
        switch (command) {
            case "crawl" -> runCrawl(Arrays.copyOfRange(args, 1, args.length));
            case "pipeline" -> runPipeline(Arrays.copyOfRange(args, 1, args.length));
            case "search" -> runSearch(Arrays.copyOfRange(args, 1, args.length));
            case "serve" -> runServe(Arrays.copyOfRange(args, 1, args.length));
            case "export" -> runExport(Arrays.copyOfRange(args, 1, args.length));
            case "benchmark" -> runBenchmark(Arrays.copyOfRange(args, 1, args.length));
            case "interactive" -> runInteractive();
            default -> {
                System.err.println("Unknown command: " + command);
                printUsage();
                System.exit(1);
            }
        }
    }

    private static void runCrawl(String[] args) throws Exception {
        if (args.length == 0 || args[0].startsWith("--")) {
            System.err.println("Missing seed URL.");
            printUsage();
            System.exit(1);
        }

        String seedUrl = args[0];
        CrawlerConfig defaults = CrawlerConfig.defaults(seedUrl);

        int workers = 4;
        int depth = 3;
        int maxPages = 100;
        String redisHost = "localhost";
        int redisPort = 6379;
        String exportPath = "index-export.json";
        Path dataRoot = Path.of("data");
        Duration delay = Duration.ofMillis(1000);
        boolean runPipeline = true;
        boolean distributed = false;
        String frontierNamespace = "default";
        Duration leaseDuration = Duration.ofSeconds(30);

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--workers" -> workers = Integer.parseInt(requireValue(args, ++i, "--workers"));
                case "--depth" -> depth = Integer.parseInt(requireValue(args, ++i, "--depth"));
                case "--max-pages" -> maxPages = Integer.parseInt(requireValue(args, ++i, "--max-pages"));
                case "--redis-host" -> redisHost = requireValue(args, ++i, "--redis-host");
                case "--redis-port" -> redisPort = Integer.parseInt(requireValue(args, ++i, "--redis-port"));
                case "--export" -> exportPath = requireValue(args, ++i, "--export");
                case "--data-dir" -> dataRoot = Path.of(requireValue(args, ++i, "--data-dir"));
                case "--delay-ms" -> delay = Duration.ofMillis(Long.parseLong(requireValue(args, ++i, "--delay-ms")));
                case "--bronze-only" -> runPipeline = false;
                case "--distributed" -> distributed = true;
                case "--frontier-namespace" -> frontierNamespace = requireValue(args, ++i, "--frontier-namespace");
                case "--lease-seconds" -> leaseDuration = Duration.ofSeconds(Long.parseLong(requireValue(args, ++i, "--lease-seconds")));
                default -> throw new IllegalArgumentException("Unknown crawl flag: " + args[i]);
            }
        }

        CrawlerConfig config = new CrawlerConfig(
                seedUrl, depth, workers, maxPages, delay, defaults.httpTimeout(),
                defaults.allowedDomain(), redisHost, redisPort, null, exportPath,
                dataRoot, PageFetcher.DEFAULT_USER_AGENT, runPipeline
        );

        System.out.printf("Starting crawl: seed=%s workers=%d depth=%d maxPages=%d domain=%s%n",
                seedUrl, workers, depth, maxPages, config.allowedDomain());
        System.out.printf("Bronze layer: %s%n", dataRoot.resolve("bronze"));

        CrawlFrontier frontier = distributed
            ? new RedisFrontier(redisHost, redisPort, frontierNamespace, leaseDuration)
            : new Frontier();
        try (CrawlerEngine engine = new CrawlerEngine(config, frontier)) {
            Thread shutdownHook = new Thread(() -> closeQuietly(engine), "crawler-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            CrawlerEngine.CrawlStats stats = engine.crawl();
            System.out.printf("Crawl complete.%n");
            System.out.printf("  Pages crawled:     %d%n", stats.pagesCrawled());
            System.out.printf("  URLs discovered:   %d%n", stats.urlsDiscovered());
            System.out.printf("  Bronze path:       %s%n", stats.bronzePath());
            if (runPipeline) {
                System.out.printf("  Silver documents:  %d%n", stats.silverDocuments());
                System.out.printf("  Gold documents:    %d%n", stats.goldDocuments());
                System.out.printf("  Indexed documents: %d%n", stats.indexedDocuments());
                System.out.printf("  Indexed terms:     %d%n", stats.indexedTerms());
            }
            System.out.printf("  JSON export:       %s%n", stats.exportPath());
            System.out.printf("  Throughput:        %.2f pages/second%n", stats.pagesPerSecond());
            System.out.printf("  Elapsed:           %d ms%n", stats.elapsedMillis());
            System.out.printf("  Redis index size:  %d bytes%n", stats.indexBytes());
            System.out.printf("  JVM heap used:     %d bytes%n", stats.jvmUsedMemoryBytes());
            removeShutdownHook(shutdownHook);
        }
    }

    private static void runBenchmark(String[] args) {
        int iterations = 500;
        int warmup = 50;
        String redisHost = "localhost";
        int redisPort = 6379;
        List<String> queries = new java.util.ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--iterations" -> iterations = Integer.parseInt(requireValue(args, ++i, "--iterations"));
                case "--warmup" -> warmup = Integer.parseInt(requireValue(args, ++i, "--warmup"));
                case "--redis-host" -> redisHost = requireValue(args, ++i, "--redis-host");
                case "--redis-port" -> redisPort = Integer.parseInt(requireValue(args, ++i, "--redis-port"));
                default -> queries.add(args[i]);
            }
        }
        if (queries.isEmpty()) {
            queries = List.of("distributed systems", "web crawler", "search engine", "java concurrency");
        }

        try (RedisInvertedIndex index = new RedisInvertedIndex(redisHost, redisPort, null)) {
            SearchBenchmark.Report report = SearchBenchmark.run(index, queries, warmup, iterations);
            System.out.printf("Search benchmark (%d iterations)%n", report.iterations());
            System.out.printf("  Throughput:       %.2f queries/second%n", report.queriesPerSecond());
            System.out.printf("  Latency p50:      %.3f ms%n", report.p50Millis());
            System.out.printf("  Latency p95:      %.3f ms%n", report.p95Millis());
            System.out.printf("  Latency p99:      %.3f ms%n", report.p99Millis());
            System.out.printf("  Redis index size: %d bytes%n", report.indexBytes());
            System.out.printf("  JVM heap used:    %d bytes%n", report.jvmUsedMemoryBytes());
        }
    }

    private static void runPipeline(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Missing pipeline stage: silver | gold | all");
            System.exit(1);
        }

        String stage = args[0].toLowerCase(Locale.ROOT);
        Path dataRoot = Path.of("data");
        String redisHost = "localhost";
        int redisPort = 6379;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--data-dir" -> dataRoot = Path.of(requireValue(args, ++i, "--data-dir"));
                case "--redis-host" -> redisHost = requireValue(args, ++i, "--redis-host");
                case "--redis-port" -> redisPort = Integer.parseInt(requireValue(args, ++i, "--redis-port"));
                default -> throw new IllegalArgumentException("Unknown pipeline flag: " + args[i]);
            }
        }

        try (RedisInvertedIndex index = new RedisInvertedIndex(redisHost, redisPort, null)) {
            PipelineOrchestrator pipeline = new PipelineOrchestrator(dataRoot, index);
            PipelineOrchestrator.PipelineStats stats = switch (stage) {
                case "silver" -> pipeline.runSilver();
                case "gold" -> pipeline.runGold();
                case "all" -> pipeline.runAll();
                default -> throw new IllegalArgumentException("Unknown pipeline stage: " + stage);
            };

            System.out.printf("Pipeline '%s' complete.%n", stage);
            System.out.printf("  Silver documents: %d%n", stats.silverDocuments());
            System.out.printf("  Gold documents:   %d%n", stats.goldDocuments());
            System.out.printf("  Redis documents:  %d%n", index.documentCount());
            System.out.printf("  Redis terms:      %d%n", index.termCount());
        }
    }

    private static void runSearch(String[] args) {
        if (args.length == 0) {
            System.err.println("Missing search query.");
            System.exit(1);
        }

        int limit = 10;
        String redisHost = "localhost";
        int redisPort = 6379;
        StringBuilder queryBuilder = new StringBuilder();

        for (int i = 0; i < args.length; i++) {
            if ("--limit".equals(args[i])) {
                limit = Integer.parseInt(requireValue(args, ++i, "--limit"));
            } else if ("--redis-host".equals(args[i])) {
                redisHost = requireValue(args, ++i, "--redis-host");
            } else if ("--redis-port".equals(args[i])) {
                redisPort = Integer.parseInt(requireValue(args, ++i, "--redis-port"));
            } else {
                if (queryBuilder.length() > 0) {
                    queryBuilder.append(' ');
                }
                queryBuilder.append(args[i]);
            }
        }

        String query = queryBuilder.toString().trim();
        if (query.isEmpty()) {
            System.err.println("Search query cannot be empty.");
            System.exit(1);
        }

        try (RedisInvertedIndex index = new RedisInvertedIndex(redisHost, redisPort, null)) {
            SearchEngine searchEngine = new SearchEngine(index);
            List<SearchResult> results = searchEngine.search(query, limit);

            if (results.isEmpty()) {
                System.out.println("No results found.");
                return;
            }

            System.out.printf("BM25 results for \"%s\":%n", query);
            int rank = 1;
            for (SearchResult result : results) {
                String title = result.title();
                String snippet = result.snippet();
                String target = title != null && !title.isBlank() ? String.format("%s (%s)", title, result.url()) : result.url();
                System.out.printf("  %d. [score=%.4f, terms=%d] %s%n",
                        rank++, result.score(), result.matchedTerms(), target);
                if (snippet != null && !snippet.isBlank()) {
                    System.out.printf("       %s%n", snippet);
                }
            }
        }
    }

    private static void runExport(String[] args) {
        String out = "index-export.json";
        String redisHost = "localhost";
        int redisPort = 6379;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--out" -> out = requireValue(args, ++i, "--out");
                case "--redis-host" -> redisHost = requireValue(args, ++i, "--redis-host");
                case "--redis-port" -> redisPort = Integer.parseInt(requireValue(args, ++i, "--redis-port"));
                default -> throw new IllegalArgumentException("Unknown export flag: " + args[i]);
            }
        }

        try (RedisInvertedIndex index = new RedisInvertedIndex(redisHost, redisPort, null)) {
            index.exportToJson(out);
            System.out.printf("Exported %d terms across %d documents to %s%n",
                    index.termCount(), index.documentCount(), out);
        }
    }

    private static void runInteractive() {
        System.out.println("Interactive BM25 search. Type 'quit' to exit.");
        try (RedisInvertedIndex index = new RedisInvertedIndex("localhost", 6379, null)) {
            SearchEngine searchEngine = new SearchEngine(index);
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("search> ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String line = scanner.nextLine().trim();
                if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                    break;
                }
                if (line.isEmpty()) {
                    continue;
                }

                List<SearchResult> results = searchEngine.search(line);
                if (results.isEmpty()) {
                    System.out.println("No results.");
                    continue;
                }
                int rank = 1;
                for (SearchResult result : results) {
                    String title = result.title();
                    String display = title != null && !title.isBlank() ? String.format("%s (%s)", title, result.url()) : result.url();
                    System.out.printf("  %d. [%.4f] %s%n", rank++, result.score(), display);
                }
            }
        }
    }

    private static void runServe(String[] args) throws Exception {
        int port = 7000;
        String redisHost = "localhost";
        int redisPort = 6379;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(requireValue(args, ++i, "--port"));
                case "--redis-host" -> redisHost = requireValue(args, ++i, "--redis-host");
                case "--redis-port" -> redisPort = Integer.parseInt(requireValue(args, ++i, "--redis-port"));
                default -> throw new IllegalArgumentException("Unknown serve flag: " + args[i]);
            }
        }

        System.out.printf("Starting HTTP server on port %d (redis=%s:%d)%n", port, redisHost, redisPort);
        try (com.crawler.api.SearchServer server = new com.crawler.api.SearchServer(port, redisHost, redisPort)) {
            server.await();
        } catch (InterruptedException e) {
            System.out.println("Server interrupted, shutting down");
        }
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[index];
    }

    private static void closeQuietly(CrawlerEngine engine) {
        try {
            engine.close();
        } catch (Exception e) {
            System.err.println("Shutdown cleanup failed: " + e.getMessage());
        }
    }

    private static void removeShutdownHook(Thread hook) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // JVM shutdown has already started; the hook will perform cleanup.
        }
    }

    private static void printUsage() {
        System.out.println("""
                Concurrent Web Crawler with Reverse Indexing

                Usage:
                  java -jar crawler.jar crawl <seed-url> [options]
                  java -jar crawler.jar pipeline <silver|gold|all> [options]
                  java -jar crawler.jar search <query...> [options]
                  java -jar crawler.jar export [options]
                  java -jar crawler.jar benchmark [queries...] [options]
                  java -jar crawler.jar interactive

                Crawl options:
                  --workers N       Number of worker threads (default: 4)
                  --depth N         Max link depth from seed (default: 3)
                  --max-pages N     Stop after N successful fetches (default: 100)
                  --delay-ms N      Per-domain delay in milliseconds (default: 1000)
                  --data-dir PATH   Medallion data root (default: data)
                  --bronze-only     Skip Silver/Gold pipeline after crawl
                  --distributed     Use a shared persistent Redis frontier
                  --frontier-namespace N  Shared crawl job name (default: default)
                  --lease-seconds N Work recovery lease (default: 30)
                  --redis-host H    Redis host (default: localhost)
                  --redis-port P    Redis port (default: 6379)
                  --export PATH     JSON export path (default: index-export.json)

                Pipeline options:
                  --data-dir PATH   Medallion data root (default: data)
                  --redis-host H    Redis host (default: localhost)
                  --redis-port P    Redis port (default: 6379)

                Search options:
                  --limit N         Max results (default: 10)
                  --redis-host H    Redis host (default: localhost)
                  --redis-port P    Redis port (default: 6379)

                                Benchmark options:
                                    --iterations N    Measured searches (default: 500)
                                    --warmup N        Warmup searches (default: 50)
                                    --redis-host H    Redis host (default: localhost)
                                    --redis-port P    Redis port (default: 6379)
                """);
    }
}
