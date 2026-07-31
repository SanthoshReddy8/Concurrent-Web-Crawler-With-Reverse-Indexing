package com.crawler.pipeline;

import com.crawler.fetch.PageFetcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BronzeStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndReadsMetadataWithTimestamp() throws Exception {
        BronzeStore store = new BronzeStore(tempDir);
        PageFetcher.FetchedPage page = new PageFetcher.FetchedPage(
                "https://example.com/page",
                "<html><body>Example</body></html>",
                200,
                "text/html",
                Map.of("content-type", "text/html")
        );

        BronzeRecord saved = store.save(page);
        Path metadataPath = store.bronzeDir().resolve(saved.id() + ".meta.json");
        BronzeRecord restored = store.readMeta(metadataPath).orElseThrow();

        assertEquals(page.url(), restored.url());
        assertEquals(saved.fetchedAt(), restored.fetchedAt());
        assertNotNull(restored.fetchedAt());
        assertTrue(Path.of(restored.htmlPath()).toFile().isFile());
    }
}
