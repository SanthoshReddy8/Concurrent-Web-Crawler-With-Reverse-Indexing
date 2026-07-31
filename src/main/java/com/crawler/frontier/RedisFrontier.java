package com.crawler.frontier;

import com.google.gson.Gson;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RedisFrontier implements CrawlFrontier {

    private static final Gson GSON = new Gson();

    private final JedisPool pool;
    private final String queueKey;
    private final String visitedKey;
    private final String leasesKey;
    private final long leaseMillis;
    private final Set<String> localLeases = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public RedisFrontier(String host, int port, String namespace, Duration leaseDuration) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(16);
        this.pool = new JedisPool(config, host, port);
        String prefix = "crawl:" + namespace + ":";
        this.queueKey = prefix + "queue";
        this.visitedKey = prefix + "visited";
        this.leasesKey = prefix + "leases";
        this.leaseMillis = leaseDuration.toMillis();
    }

    @Override
    public boolean offer(CrawlTask task) {
        if (closed) {
            return false;
        }
        String payload = encode(task);
        try (Jedis jedis = pool.getResource()) {
            if (jedis.sadd(visitedKey, task.url()) == 0) {
                return false;
            }
            jedis.rpush(queueKey, payload);
            return true;
        }
    }

    @Override
    public Optional<CrawlTask> poll(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!closed && System.currentTimeMillis() <= deadline) {
            recoverExpired();
            try (Jedis jedis = pool.getResource()) {
                String payload = jedis.lpop(queueKey);
                if (payload != null) {
                    jedis.zadd(leasesKey, System.currentTimeMillis() + leaseMillis, payload);
                    localLeases.add(payload);
                    return Optional.of(decode(payload));
                }
            }
            Thread.sleep(Math.min(50, Math.max(1, timeoutMs)));
        }
        return Optional.empty();
    }

    @Override
    public void taskFinished(CrawlTask task) {
        String payload = encode(task);
        localLeases.remove(payload);
        try (Jedis jedis = pool.getResource()) {
            jedis.zrem(leasesKey, payload);
        }
    }

    private void recoverExpired() {
        try (Jedis jedis = pool.getResource()) {
            List<String> expired = jedis.zrangeByScore(leasesKey, 0, System.currentTimeMillis(), 0, 100);
            for (String payload : expired) {
                if (jedis.zrem(leasesKey, payload) > 0) {
                    jedis.lpush(queueKey, payload);
                }
            }
        }
    }

    @Override
    public boolean isIdle() {
        recoverExpired();
        try (Jedis jedis = pool.getResource()) {
            return jedis.llen(queueKey) == 0 && jedis.zcard(leasesKey) == 0;
        }
    }

    @Override
    public int queuedCount() {
        try (Jedis jedis = pool.getResource()) {
            return Math.toIntExact(jedis.llen(queueKey));
        }
    }

    @Override
    public int visitedCount() {
        try (Jedis jedis = pool.getResource()) {
            return Math.toIntExact(jedis.scard(visitedKey));
        }
    }

    @Override
    public void close() {
        closed = true;
        try (Jedis jedis = pool.getResource()) {
            for (String payload : localLeases) {
                if (jedis.zrem(leasesKey, payload) > 0) {
                    jedis.lpush(queueKey, payload);
                }
            }
        } finally {
            localLeases.clear();
            pool.close();
        }
    }

    private static String encode(CrawlTask task) {
        return GSON.toJson(task);
    }

    private static CrawlTask decode(String payload) {
        return GSON.fromJson(payload, CrawlTask.class);
    }
}