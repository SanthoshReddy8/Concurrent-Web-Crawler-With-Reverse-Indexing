package com.crawler.pipeline;

import com.crawler.fetch.PageFetcher;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Writes raw HTML and HTTP metadata to the Bronze layer.
 */
public class BronzeStore {

    private static final Gson GSON = PipelineJson.create(true);

    private final Path bronzeDir;

    public BronzeStore(Path dataRoot) {
        this.bronzeDir = dataRoot.resolve("bronze");
    }

    public Path bronzeDir() {
        return bronzeDir;
    }

    public BronzeRecord save(PageFetcher.FetchedPage page) throws IOException {
        Files.createDirectories(bronzeDir);

        String id = hashUrl(page.url());
        Path htmlPath = bronzeDir.resolve(id + ".html");
        Path metaPath = bronzeDir.resolve(id + ".meta.json");

        Files.writeString(htmlPath, page.html(), StandardCharsets.UTF_8);

        BronzeRecord record = new BronzeRecord(
                id,
                page.url(),
                page.statusCode(),
                page.contentType(),
                Instant.now(),
                page.headers(),
                htmlPath.toString()
        );

        Files.writeString(metaPath, GSON.toJson(record), StandardCharsets.UTF_8);
        return record;
    }

    public Optional<BronzeRecord> readMeta(Path metaPath) throws IOException {
        if (!Files.exists(metaPath)) {
            return Optional.empty();
        }
        BronzeRecord record = GSON.fromJson(Files.readString(metaPath, StandardCharsets.UTF_8), BronzeRecord.class);
        return Optional.ofNullable(record);
    }

    public static String hashUrl(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
