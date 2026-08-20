package bt.gov.jdwnrh.scheduler.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * See docs/designs/jdwnrh-scheduler.md, "Scope Expansion" -> E4: the admin
 * time-travel toggle advances the app's simulated "now" for demo purposes
 * (e.g. jump forward a day to show a 24h reminder fire without waiting a
 * real day). Every service already reads time through the injected Clock
 * bean (see ClockConfig), so this is a drop-in replacement — real time plus
 * a mutable offset, resolved fresh on every instant() call (never cached),
 * so slot generation and the exclusion constraint never see two different
 * "nows" in one operation, and time keeps moving forward in real-time even
 * while offset — this isn't a frozen clock, it's a shifted one.
 *
 * offset is an AtomicReference for thread-safe, immediately-visible updates
 * across the request threads that read it concurrently (every request
 * reads the same shared clock).
 */
public class TimeTravelClock extends Clock {

    private final Clock baseClock;
    private final AtomicReference<Duration> offset;

    public TimeTravelClock(Clock baseClock) {
        this(baseClock, Duration.ZERO);
    }

    private TimeTravelClock(Clock baseClock, Duration initialOffset) {
        this.baseClock = Objects.requireNonNull(baseClock);
        this.offset = new AtomicReference<>(initialOffset);
    }

    @Override
    public ZoneId getZone() {
        return baseClock.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        // Starts from whatever offset is currently active — a zone-adjusted view
        // of this clock must still reflect it, not reset to zero.
        return new TimeTravelClock(baseClock.withZone(zone), offset.get());
    }

    @Override
    public Instant instant() {
        return baseClock.instant().plus(offset.get());
    }

    public void advance(Duration amount) {
        offset.updateAndGet(current -> current.plus(amount));
    }

    public void reset() {
        offset.set(Duration.ZERO);
    }

    public Duration getOffset() {
        return offset.get();
    }
}
