package bt.gov.jdwnrh.scheduler.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

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
 */
@Component
public class InMemoryRateLimiter {

    // Known limitation: entries for keys (IPs) that go idle are never evicted from the
    // outer map, only the per-key deque self-prunes. Fine at this app's scale/lifetime;
    // a long-running production deployment would want a periodic sweep or a TTL cache.
    private final Map<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** true if the caller is under the limit (and the attempt is now recorded); false if they should be rejected. */
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        Instant now = clock.instant();
        Deque<Instant> attempts = attemptsByKey.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        synchronized (attempts) {
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
