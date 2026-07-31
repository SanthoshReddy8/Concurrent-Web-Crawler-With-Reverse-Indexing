package com.crawler.index;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Redis-backed inverted index with BM25 metadata.
 *
 * <p>Schema:
 * <ul>
 *   <li>{@code idx:term:{word}} -> hash of url -> term frequency</li>
 *   <li>{@code idx:df:{word}} -> document frequency (number of docs containing term)</li>
 *   <li>{@code idx:meta:doclen} -> hash of url -> total token count</li>
 *   <li>{@code idx:meta:total_doclen} -> sum of all document lengths</li>
 *   <li>{@code idx:meta:terms} -> set of all indexed terms</li>
 *   <li>{@code idx:meta:docs} -> set of all indexed document URLs</li>
 * </ul>
 */
public class RedisInvertedIndex implements InvertedIndex {

    private static final String TERM_PREFIX = "idx:term:";
    private static final String DF_PREFIX = "idx:df:";
    private static final String META_TERMS = "idx:meta:terms";
    private static final String META_DOCS = "idx:meta:docs";
    private static final String META_TITLE = "idx:meta:title";
    private static final String META_SNIPPET = "idx:meta:snippet";
    private static final String META_DOC_LEN = "idx:meta:doclen";
    private static final String META_TOTAL_DOC_LEN = "idx:meta:total_doclen";

    private final JedisPool pool;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public RedisInvertedIndex(String host, int port, String password) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(32);
        if (password == null || password.isBlank()) {
            this.pool = new JedisPool(config, host, port);
        } else {
            this.pool = new JedisPool(config, host, port, 2000, password);
        }
    }

    @Override
    public void indexDocument(String url, Map<String, Integer> termFrequencies) {
        if (termFrequencies.isEmpty()) {
            return;
        }

        int documentLength = termFrequencies.values().stream().mapToInt(Integer::intValue).sum();

        try (Jedis jedis = pool.getResource()) {
            boolean isNewDocument = jedis.sadd(META_DOCS, url) > 0;

            if (!isNewDocument) {
                removeExistingDocument(jedis, url);
            }

            jedis.hset(META_DOC_LEN, url, String.valueOf(documentLength));
            jedis.incrBy(META_TOTAL_DOC_LEN, documentLength);

            for (Map.Entry<String, Integer> entry : termFrequencies.entrySet()) {
                String term = entry.getKey().toLowerCase(Locale.ROOT);
                int frequency = entry.getValue();
                String termKey = TERM_PREFIX + term;

                jedis.hset(termKey, url, String.valueOf(frequency));
                jedis.incr(DF_PREFIX + term);
                jedis.sadd(META_TERMS, term);
            }
        }
    }

    private void removeExistingDocument(Jedis jedis, String url) {
        String oldLength = jedis.hget(META_DOC_LEN, url);
        if (oldLength != null) {
            jedis.incrBy(META_TOTAL_DOC_LEN, -Long.parseLong(oldLength));
        }

        Set<String> terms = jedis.smembers(META_TERMS);
        for (String term : terms) {
            String termKey = TERM_PREFIX + term;
            if (jedis.hexists(termKey, url)) {
                jedis.hdel(termKey, url);
                jedis.decr(DF_PREFIX + term);
            }
        }
        jedis.hdel(META_DOC_LEN, url);
    }

    @Override
    public Map<String, Integer> getPostings(String term) {
        String key = TERM_PREFIX + term.toLowerCase(Locale.ROOT);
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> raw = jedis.hgetAll(key);
            Map<String, Integer> postings = new HashMap<>();
            for (Map.Entry<String, String> entry : raw.entrySet()) {
                postings.put(entry.getKey(), Integer.parseInt(entry.getValue()));
            }
            return postings;
        }
    }

    @Override
    public long getDocumentFrequency(String term) {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(DF_PREFIX + term.toLowerCase(Locale.ROOT));
            return value == null ? 0L : Long.parseLong(value);
        }
    }

    @Override
    public int getDocumentLength(String url) {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.hget(META_DOC_LEN, url);
            return value == null ? 0 : Integer.parseInt(value);
        }
    }

    @Override
    public String getDocumentTitle(String url) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.hget(META_TITLE, url);
        }
    }

    @Override
    public String getDocumentSnippet(String url) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.hget(META_SNIPPET, url);
        }
    }

    @Override
    public void setDocumentMetadata(String url, String title, String snippet) {
        try (Jedis jedis = pool.getResource()) {
            if (title != null) {
                jedis.hset(META_TITLE, url, title);
            }
            if (snippet != null) {
                jedis.hset(META_SNIPPET, url, snippet);
            }
        }
    }

    @Override
    public long getTotalDocumentLength() {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(META_TOTAL_DOC_LEN);
            return value == null ? 0L : Long.parseLong(value);
        }
    }

    @Override
    public void exportToJson(String path) {
        Map<String, Object> export = new HashMap<>();
        export.put("documentCount", documentCount());
        export.put("averageDocumentLength", averageDocumentLength());
        export.put("terms", buildTermExport());

        String json = gson.toJson(export);
        try {
            Files.writeString(Path.of(path), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to export index to " + path, e);
        }
    }

    private Map<String, Object> buildTermExport() {
        Map<String, Object> terms = new HashMap<>();
        try (Jedis jedis = pool.getResource()) {
            for (String term : jedis.smembers(META_TERMS)) {
                Map<String, Object> termData = new HashMap<>();
                termData.put("df", jedis.get(DF_PREFIX + term));
                termData.put("postings", jedis.hgetAll(TERM_PREFIX + term));
                terms.put(term, termData);
            }
        }
        return terms;
    }

    public double averageDocumentLength() {
        long count = documentCount();
        if (count == 0) {
            return 0.0;
        }
        return (double) getTotalDocumentLength() / count;
    }

    @Override
    public long documentCount() {
        try (Jedis jedis = pool.getResource()) {
            return jedis.scard(META_DOCS);
        }
    }

    @Override
    public long termCount() {
        try (Jedis jedis = pool.getResource()) {
            return jedis.scard(META_TERMS);
        }
    }

    public long indexSizeBytes() {
        long total = 0;
        try (Jedis jedis = pool.getResource()) {
            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams params = new ScanParams().match("idx:*").count(100);
            do {
                ScanResult<String> scan = jedis.scan(cursor, params);
                for (String key : scan.getResult()) {
                    Long bytes = jedis.memoryUsage(key);
                    if (bytes != null) {
                        total += bytes;
                    }
                }
                cursor = scan.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
        }
        return total;
    }

    public void clear() {
        try (Jedis jedis = pool.getResource()) {
            Set<String> keys = new HashSet<>();
            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams params = new ScanParams().match("idx:*").count(100);
            do {
                ScanResult<String> scan = jedis.scan(cursor, params);
                keys.addAll(scan.getResult());
                cursor = scan.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));

            if (!keys.isEmpty()) {
                jedis.del(keys.toArray(String[]::new));
            }
        }
    }

    @Override
    public void close() {
        pool.close();
    }
}
