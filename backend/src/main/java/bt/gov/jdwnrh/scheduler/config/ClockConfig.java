package bt.gov.jdwnrh.scheduler.config;

import java.time.Clock;
import java.time.ZoneOffset;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Every service reads "now" through an injected {@link Clock}, never
 * {@code Instant.now()}/{@code LocalDate.now()} directly. That's what makes
 * the admin time-travel toggle (see docs/designs/jdwnrh-scheduler.md,
 * "Scope Expansion" -> E4, and TimeTravelClock) a drop-in swap instead of a
 * scattered refactor, and makes date-dependent tests (slot generation, the
 * reminder job) trivial to write without waiting on real time.
 *
 * Declared as the concrete TimeTravelClock type (not just Clock) so
 * AdminTimeTravelController can inject the same singleton and call its
 * advance()/reset() — every other bean in the app injects it as plain
 * Clock, which TimeTravelClock satisfies (it extends Clock).
 *
 * The clock always runs in UTC — all timestamps are stored UTC and rendered
 * in Asia/Thimphu only at the presentation/notification layer.
 */
@Configuration
public class ClockConfig {

    @Bean
    public TimeTravelClock clock() {
        return new TimeTravelClock(Clock.systemUTC().withZone(ZoneOffset.UTC));
    }
}
