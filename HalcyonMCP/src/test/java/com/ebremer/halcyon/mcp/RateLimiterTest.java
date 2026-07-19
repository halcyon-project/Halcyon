package com.ebremer.halcyon.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP-17: the token bucket is per-principal, bursts to capacity, refuses when
 * empty, and refills over time — all deterministic because time is injected.
 */
class RateLimiterTest {

    @Test
    void burstsToCapacityThenRefuses() {
        RateLimiter rl = new RateLimiter(3, 60_000);
        long t = 1_000_000L;
        assertTrue(rl.tryAcquire("alice", t));
        assertTrue(rl.tryAcquire("alice", t));
        assertTrue(rl.tryAcquire("alice", t));
        assertFalse(rl.tryAcquire("alice", t), "the 4th call in the same instant is over capacity");
    }

    @Test
    void refillsOverTime() {
        RateLimiter rl = new RateLimiter(2, 60_000); // 2 per minute
        long t = 5_000_000L;
        assertTrue(rl.tryAcquire("bob", t));
        assertTrue(rl.tryAcquire("bob", t));
        assertFalse(rl.tryAcquire("bob", t));
        // Half a minute later → one token back.
        assertTrue(rl.tryAcquire("bob", t + 30_000), "a token refills after half the window");
        assertFalse(rl.tryAcquire("bob", t + 30_000));
    }

    @Test
    void principalsAreIndependent() {
        RateLimiter rl = new RateLimiter(1, 60_000);
        long t = 2_000_000L;
        assertTrue(rl.tryAcquire("alice", t));
        assertFalse(rl.tryAcquire("alice", t));
        assertTrue(rl.tryAcquire("bob", t), "bob's bucket is his own");
    }

    @Test
    void noKeyIsAlwaysAllowed() {
        RateLimiter rl = new RateLimiter(1, 60_000);
        assertTrue(rl.tryAcquire(null, 0));
        assertTrue(rl.tryAcquire("", 0));
        assertTrue(rl.tryAcquire(null, 0), "an absent key is never limited (auth already gated it)");
    }
}
