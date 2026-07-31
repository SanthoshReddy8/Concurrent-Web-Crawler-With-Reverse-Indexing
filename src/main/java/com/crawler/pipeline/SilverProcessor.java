package com.crawler.pipeline;

import com.crawler.fetch.PageParser;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads Bronze HTML and writes cleansed Silver JSON documents.
 */
public class SilverProcessor {

    private static final Gson GSON = PipelineJson.create(true);

    private final Path bronzeDir;
    private final Path silverDir;
    private final PageParser parser = new PageParser();

    public SilverProcessor(Path dataRoot) {
        this.bronzeDir = dataRoot.resolve("bronze");
        this.silverDir = dataRoot.resolve("silver");
    }

    public int processAll() throws IOException {
        Files.createDirectories(silverDir);
        if (!Files.isDirectory(bronzeDir)) {
            return 0;
        }

        int processed = 0;
        try (Stream<Path> metaFiles = Files.list(bronzeDir).filter(p -> p.toString().endsWith(".meta.json"))) {
            List<Path> paths = metaFiles.toList();
            for (Path metaPath : paths) {
                if (processOne(metaPath)) {
                    processed++;
                }
            }
        }
        return processed;
    }

    public boolean processOne(Path metaPath) throws IOException {
        BronzeStore store = new BronzeStore(bronzeDir.getParent());
        var recordOptional = store.readMeta(metaPath);
        if (recordOptional.isEmpty()) {
            return false;
        }

        BronzeRecord record = recordOptional.get();
        Path htmlPath = Path.of(record.htmlPath());
        if (!Files.exists(htmlPath)) {
            htmlPath = bronzeDir.resolve(record.id() + ".html");
        }
        if (!Files.exists(htmlPath)) {
            return false;
        }

        String html = Files.readString(htmlPath, StandardCharsets.UTF_8);
        PageParser.ParsedPage parsed = parser.parse(record.url(), html);

        int documentLength = parsed.termFrequencies().values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        SilverDocument silver = new SilverDocument(
                record.id(),
                parsed.url(),
                parsed.title(),
                parsed.links(),
                parsed.termFrequencies(),
                documentLength,
                Instant.now(),
                record.id()
        );

        Path outPath = silverDir.resolve(record.id() + ".json");
        Files.writeString(outPath, GSON.toJson(silver), StandardCharsets.UTF_8);
        return true;
    }

    public Path silverDir() {
        return silverDir;
    }
}
