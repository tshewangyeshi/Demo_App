package bt.gov.jdwnrh.scheduler.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Basic sliding-window rate limiter — see docs/designs/jdwnrh-scheduler.md,
 * "Scope Expansion" -> E2: the public appointment lookup needs "basic rate
 * limiting" to close slow-brute-force risk (reference number + last name
 * together aren't secret enough to allow unlimited guessing). Deliberately
 * in-memory, not Redis-backed: this app runs as a single instance for this
 * scope (see the design doc's reminder-job distributed-lock discussion,
 * which is the one place that DOES need to survive multiple instances) — an
 * in-memory limiter is the honest, unglamorous-but-correct choice here, not
 * a shortcut. Would need a shared store to hold under horizontal scaling.
 *
 * Reads "now" through the injected Clock (like everything else in this app)
 * so the window respects the admin time-travel toggle too.
 *
 * Bounded to MAX_TRACKED_KEYS distinct keys. This was originally an
 * unbounded ConcurrentHashMap, safe when every key was a real IP address
 * (bounded by actual attacker IP churn). Once AuthController started keying
 * a bucket per EMAIL — attacker-controlled, validated only for @Email
 * *format*, never that the address exists — that assumption broke: an
 * unauthenticated caller could POST login with an endless stream of made-up
 * emails and grow this map without bound, a pre-auth memory-exhaustion path
 * (see /review, 2026-08-21).
 *
 * The FIRST fix for that (plain access-order LinkedHashMap + removeEldestEntry,
 * classic LRU) introduced a worse bug: an attacker with a modest IP botnet
 * could flood the map with thousands of throwaway keys to push a targeted
 * victim's still-active per-email lockout bucket out of the map via pure LRU
 * pressure, silently resetting that victim's attempt counter mid-attack —
 * defeating the exact protection the per-email bucket exists to provide (see
 * /review adversarial pass, 2026-08-21, Finding 2). Eviction now prefers an
 * already-expired ("cold") entry over an active ("hot", still within its own
 * window) one: on eviction pressure, it scans up to EVICTION_SCAN_DEPTH of
 * the least-recently-touched entries for one that's expired under its OWN
 * window (a "lookup:" key's 5-minute window is pruned against its own
 * duration, not whatever window the CURRENT caller happens to be using —
 * each entry remembers the window it was created with), and evicts that
 * instead. Only if nothing in the scan window is cold does it fall back to
 * evicting the true least-recently-touched entry regardless of hotness —
 * the map must never exceed MAX_TRACKED_KEYS (that unbounded-growth path is
 * the original vulnerability this class exists to close), so a hard bound
 * always wins over protecting any single bucket. This raises the bar from
 * "outlast one entry in LRU order" to "keep EVICTION_SCAN_DEPTH-worth of
 * entries simultaneously live," without reopening the memory-exhaustion gap.
 *
 * Single coarse lock instead of per-deque synchronized nesting: simpler to
 * reason about, and this isn't a hot enough path (auth-adjacent endpoints
 * only) to need finer-grained concurrency.
 */
@Component
public class InMemoryRateLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    /**
     * How many of the least-recently-touched entries to inspect for an
     * evictable cold (fully expired) one before falling back to evicting
     * the true least-recently-touched entry regardless of hotness. Bounded
     * work per eviction, only paid when the map is already at capacity.
     */
    private static final int EVICTION_SCAN_DEPTH = 256;

    private final Map<String, Bucket> attemptsByKey = new LinkedHashMap<>(16, 0.75f, true);
    private final Object lock = new Object();
    private final Clock clock;

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** true if the caller is under the limit (and the attempt is now recorded); false if they should be rejected. */
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        Instant now = clock.instant();

        synchronized (lock) {
            Bucket bucket = attemptsByKey.get(key);
            if (bucket == null) {
                makeRoomIfNeeded(now);
                bucket = new Bucket(window);
                attemptsByKey.put(key, bucket);
            }

            prune(bucket, now);
            if (bucket.attempts.size() >= maxAttempts) {
                return false;
            }
            bucket.attempts.addLast(now);
            return true;
        }
    }

    /** Evicts one entry if the map is already at capacity, preferring a cold (expired) one over a hot one. */
    private void makeRoomIfNeeded(Instant now) {
        if (attemptsByKey.size() < MAX_TRACKED_KEYS) {
            return;
        }

        Iterator<Map.Entry<String, Bucket>> it = attemptsByKey.entrySet().iterator();
        Map.Entry<String, Bucket> fallback = null;
        int scanned = 0;
        while (it.hasNext() && scanned < EVICTION_SCAN_DEPTH) {
            Map.Entry<String, Bucket> candidate = it.next();
            if (fallback == null) {
                fallback = candidate;
            }
            prune(candidate.getValue(), now);
            if (candidate.getValue().attempts.isEmpty()) {
                it.remove();
                return;
            }
            scanned++;
        }

        // Nothing cold within the scan window — every one of the
        // least-recently-touched EVICTION_SCAN_DEPTH entries is still
        // actively rate-limiting someone. Evict the true
        // least-recently-touched one anyway: the hard bound always wins.
        attemptsByKey.remove(fallback.getKey());
    }

    private static void prune(Bucket bucket, Instant now) {
        Instant cutoff = now.minus(bucket.window);
        while (!bucket.attempts.isEmpty() && bucket.attempts.peekFirst().isBefore(cutoff)) {
            bucket.attempts.pollFirst();
        }
    }

    /** A key's attempt history plus the window it was created with, so eviction can prune it correctly later. */
    private static final class Bucket {
        final Deque<Instant> attempts = new LinkedList<>();
        final Duration window;

        Bucket(Duration window) {
            this.window = window;
        }
    }
}
