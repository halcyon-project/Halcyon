package com.ebremer.halcyon.mcp;

import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP-17: a per-principal token bucket — one authenticated WebID cannot turn
 * the endpoint into a firehose. Burst up to {@code capacity}, refilling at
 * {@code capacity} tokens per {@code windowMillis}; a request with no token
 * left is refused (the filter answers 429).
 *
 * <p>Time is passed in, not read, so the refill is deterministic under test.
 * Keyed by WebID (the verified identity), never by client or connection —
 * the limit is on the <em>person</em>, so it holds across reconnects and
 * across whichever MCP client they use.
 */
public final class RateLimiter {

    private final int capacity;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int capacity, long windowMillis) {
        this.capacity = Math.max(1, capacity);
        this.windowMillis = Math.max(1, windowMillis);
    }

    private static final class Bucket {
        double tokens;
        long lastRefillMillis;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefillMillis = now;
        }
    }

    /**
     * Try to spend one token for {@code key} at time {@code nowMillis}. Returns
     * {@code true} when granted, {@code false} when the principal is over its
     * rate. A null/blank key is always granted (an unauthenticated request
     * never reaches here — the auth filter refuses it first).
     */
    public boolean tryAcquire(String key, long nowMillis) {
        if (key == null || key.isBlank()) {
            return true;
        }
        double refillPerMilli = (double) capacity / windowMillis;
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(capacity, nowMillis));
        synchronized (b) {
            long elapsed = Math.max(0, nowMillis - b.lastRefillMillis);
            b.tokens = Math.min(capacity, b.tokens + elapsed * refillPerMilli);
            b.lastRefillMillis = nowMillis;
            if (b.tokens >= 1.0) {
                b.tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    public int capacity() {
        return capacity;
    }

    public long windowMillis() {
        return windowMillis;
    }
}
