package bt.gov.jdwnrh.scheduler.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
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
 * Bounded to MAX_TRACKED_KEYS distinct keys via an access-order LinkedHashMap
 * (classic LRU-via-removeEldestEntry). This was originally an unbounded
 * ConcurrentHashMap, safe when every key was a real IP address (bounded by
 * actual attacker IP churn). Once AuthController started keying a bucket per
 * EMAIL — attacker-controlled, validated only for @Email *format*, never
 * that the address exists — that assumption broke: an unauthenticated caller
 * could POST login with an endless stream of made-up emails and grow this
 * map without bound, a pre-auth memory-exhaustion path (see /review,
 * 2026-08-21). Single coarse lock instead of the old ConcurrentHashMap +
 * per-deque synchronized nesting: simpler to reason about, and this isn't a
 * hot enough path (auth-adjacent endpoints only) to need finer-grained
 * concurrency.
 */
@Component
public class InMemoryRateLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Deque<Instant>> attemptsByKey = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
            return size() > MAX_TRACKED_KEYS;
        }
    };
    private final Object lock = new Object();
    private final Clock clock;

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** true if the caller is under the limit (and the attempt is now recorded); false if they should be rejected. */
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        Instant now = clock.instant();

        synchronized (lock) {
            Deque<Instant> attempts = attemptsByKey.computeIfAbsent(key, k -> new LinkedList<>());

            Instant cutoff = now.minus(window);
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
                attempts.pollFirst();
            }
            if (attempts.size() >= maxAttempts) {
                return false;
            }
            attempts.addLast(now);
            return true;
        }
    }
}
