package bt.gov.jdwnrh.scheduler.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Regression: /review, 2026-08-21 — the rate limiter guarding login/register
// had zero test coverage before this, despite being the fix for a real
// brute-force gap. A silent regression here (e.g. an inverted condition,
// or the map cap breaking legitimate use) would remove auth protection
// with nothing to catch it.
class InMemoryRateLimiterTest {

    @Test
    void allowsUpToMaxAttemptsThenBlocks() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(fixedClockAt("2026-08-21T00:00:00Z"));

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("key", 5, Duration.ofMinutes(15)), "attempt " + (i + 1) + " should be allowed");
        }
        assertFalse(limiter.tryAcquire("key", 5, Duration.ofMinutes(15)), "6th attempt within the window must be rejected");
    }

    @Test
    void distinctKeysHaveIndependentBudgets() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(fixedClockAt("2026-08-21T00:00:00Z"));

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("login-email:a@example.com", 5, Duration.ofMinutes(15)));
        }
        assertFalse(limiter.tryAcquire("login-email:a@example.com", 5, Duration.ofMinutes(15)));
        // A different key (a different account) is not affected by a@example.com's exhausted budget.
        assertTrue(limiter.tryAcquire("login-email:b@example.com", 5, Duration.ofMinutes(15)));
    }

    @Test
    void allowsAgainOnceOldAttemptsAgeOutOfTheWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-21T00:00:00Z"));
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);
        Duration window = Duration.ofMinutes(15);

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("key", 5, window));
        }
        assertFalse(limiter.tryAcquire("key", 5, window));

        // Move past the window — the 5 old attempts should be pruned, freeing the budget again.
        clock.advance(Duration.ofMinutes(16));
        assertTrue(limiter.tryAcquire("key", 5, window), "attempts outside the window must be pruned and re-allowed");
    }

    @Test
    void tracksUnboundedKeyCardinalityWithoutExceedingTheHardCap() {
        // Guards the exact bug this rewrite fixed: an attacker-controlled key
        // (e.g. AuthController's per-email login bucket, keyed on unverified
        // input) must not grow the backing map without bound. 10,001 distinct
        // keys against a 10,000 cap should never throw or hang — LRU eviction
        // silently drops the oldest entry instead.
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(fixedClockAt("2026-08-21T00:00:00Z"));

        for (int i = 0; i < 10_001; i++) {
            assertTrue(limiter.tryAcquire("attacker-key-" + i, 5, Duration.ofMinutes(15)));
        }
        // The most recently used key must still have its own fresh budget (proves the
        // map is still functioning correctly after eviction started, not just not-crashing).
        assertTrue(limiter.tryAcquire("attacker-key-10000", 5, Duration.ofMinutes(15)));
    }

    // Regression: /review adversarial pass, 2026-08-21, Finding 2 — pure LRU
    // eviction let an attacker flood the map with throwaway keys to evict a
    // targeted victim's still-active per-email lockout bucket, silently
    // resetting their attempt counter mid-attack. Proves the fix: a victim
    // bucket that's still hot (at its own limit, within its own window)
    // survives eviction pressure even when it's the OLDEST (most LRU-vulnerable)
    // entry in the map, because eviction now scans ahead for an already-cold
    // (expired-under-its-own-window) entry first and evicts that instead.
    @Test
    void hotVictimBucketSurvivesEvictionPressureFromExpiredDecoyKeys() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-21T00:00:00Z"));
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);
        Duration victimWindow = Duration.ofMinutes(15);
        Duration decoyWindow = Duration.ofMinutes(1);

        // Victim's bucket is inserted FIRST (worst case: the true LRU-oldest
        // entry once nothing touches it again) and filled to its limit —
        // "hot", actively blocking further attempts.
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("login-email:victim@example.com", 5, victimWindow));
        }
        assertFalse(limiter.tryAcquire("login-email:victim@example.com", 5, victimWindow),
                "victim should already be locked out before the flood starts");

        // Fill the map to exactly capacity with decoys — a SHORT window
        // (unlike the victim's long one), one attempt each, all more
        // recently touched than the victim so they sit ahead of it in LRU
        // order. 9,999 decoys + 1 victim = 10,000 = MAX_TRACKED_KEYS.
        for (int i = 0; i < 9_999; i++) {
            assertTrue(limiter.tryAcquire("decoy-" + i, 1, decoyWindow));
        }

        // Move past the decoys' own (short) window but well within the
        // victim's (long) one, then insert one more new key — this is the
        // flood's overflow request that forces an eviction decision.
        clock.advance(Duration.ofMinutes(2));
        assertTrue(limiter.tryAcquire("flood-overflow-key", 1, decoyWindow));

        // The victim must still be locked out — its bucket was NOT the one
        // evicted, even though it was the true LRU-oldest entry.
        assertFalse(limiter.tryAcquire("login-email:victim@example.com", 5, victimWindow),
                "victim's lockout must survive eviction pressure from expired decoy keys");
    }

    private static Clock fixedClockAt(String isoInstant) {
        return Clock.fixed(Instant.parse(isoInstant), ZoneOffset.UTC);
    }

    /** A settable Clock for testing window expiry without real sleeps. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
