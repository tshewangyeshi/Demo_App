package bt.gov.jdwnrh.scheduler.config;

import java.time.Clock;
import java.time.ZoneOffset;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Every service reads "now" through an injected {@link Clock}, never
 * {@code Instant.now()}/{@code LocalDate.now()} directly. This is what lets
 * the admin time-travel toggle (see docs/designs/jdwnrh-scheduler.md,
 * "Scope Expansion" -> E4) override the simulated current time later without
 * a scattered refactor, and makes date-dependent tests (slot generation,
 * the reminder job) trivial to write without waiting on real time.
 *
 * The clock always runs in UTC — all timestamps are stored UTC and rendered
 * in Asia/Thimphu only at the presentation/notification layer.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC().withZone(ZoneOffset.UTC);
    }
}
