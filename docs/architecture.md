# Architecture Decisions and Scaling Limits

## System Boundaries

The crawler is split into three independently rerunnable stages:

1. Crawl writes immutable raw HTML and metadata to Bronze.
2. Silver parses HTML into normalized documents and term frequencies.
3. Gold replaces each document's postings and metadata in Redis.

The HTTP search service reads only Gold. This isolates query latency from crawling and allows Bronze/Silver replay after parser or ranking changes.

## Decision: Redis Frontier Is Optional

The in-memory frontier remains the default for local runs because it is faster and requires no queue cleanup. `--distributed` selects a Redis queue, visited set, and sorted-set lease table scoped by `--frontier-namespace`.

Benefits:

- Multiple JVMs share deduplication and queued work.
- Redis AOF persists queue state across process restarts.
- Leases recover work after crashes; graceful shutdown returns leases immediately.

Tradeoffs:

- Delivery is at least once, so a page can be fetched twice around lease expiry.
- The visited set grows with every discovered URL.
- Redis is a coordination bottleneck and single failure domain in the local Compose topology.
- A namespace is a crawl-job identity and must not be accidentally reused for unrelated seeds.

## Decision: Bounded Retry With Exponential Backoff

Network failures, HTTP `429`, and `5xx` responses are transient and retry up to three attempts. Other `4xx` responses fail immediately. Backoff starts at 200 ms and doubles per attempt. Domain rate limiting still applies before the first page fetch.

This bounds latency and avoids infinite retry storms. A production crawler would add jitter, respect `Retry-After`, persist retry counts, and move repeatedly failing URLs to a dead-letter queue.

## Failure Semantics

| Failure | Behavior |
|---------|----------|
| Worker/JVM stops cleanly | Workers are interrupted and local Redis leases return to the queue |
| Worker/JVM crashes | Another node recovers the URL after lease expiration |
| Transient HTTP failure | Bounded exponential retry |
| Bronze write failure | Structured error log; leased task is acknowledged after the attempt |
| Redis unavailable | Health endpoint fails; crawl/index operations fail rather than returning false success |
| Pipeline rerun | Silver files are replaced; Gold documents replace old postings |

## Scaling Limits

- Redis indexing currently performs many commands per document and scans all terms when replacing a document. Large indexes should use pipelining and store a per-document term set.
- One Redis instance stores the frontier and index. Production should separate coordination and serving Redis, then use managed replication/backups or cluster sharding.
- Bronze/Silver use a local filesystem. Multiple crawler hosts need shared object storage such as S3/GCS/Azure Blob with idempotent keys.
- The local per-domain limiter is not coordinated across crawler nodes. A distributed token bucket is required to enforce a global politeness rate.
- `robots.txt` cache is process-local and has no stale refresh. A shared cache with TTL is appropriate at larger scale.
- Search computes candidates in the API process and performs multiple Redis reads. High query volume should pipeline reads, cache document metadata, and consider a dedicated search engine.
- The Redis frontier's queue/lease operations are intentionally simple. Very high throughput calls for Lua scripts or Redis Streams consumer groups to tighten atomicity and observability.

## Benchmark Method

Do not compare numbers without controlling the environment. Record:

- CPU model/core limit, RAM limit, OS, JVM version, and heap settings.
- Redis version, local/remote topology, persistence mode, and network latency.
- Number of documents, unique terms, total index bytes, and query list.
- Worker count, domain delay, page depth, and target behavior for crawl tests.
- Warmup count, measured iterations, p50/p95/p99 latency, and throughput.

The built-in search benchmark is a repeatable microbenchmark for regression detection, not a substitute for JMH. The k6 scenario measures end-to-end HTTP behavior under concurrent load.

## Production Follow-ups

- TLS and authentication at an ingress/API gateway.
- Managed Redis with encryption, backups, and alerts.
- Object storage for Bronze/Silver data.
- Distributed rate limiting and robots cache.
- Dead-letter queue and retry-attempt persistence.
- OpenTelemetry traces and alert rules for latency, errors, lease age, and queue depth.