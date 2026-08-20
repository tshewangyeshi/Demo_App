package bt.gov.jdwnrh.scheduler.appointment;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

/**
 * Format is explicitly "decided at implementation time" per the design doc.
 * JDW-{year}-{6-digit}. Collisions are astronomically unlikely (1 in a
 * million per year) but not impossible — BookingService checks for one
 * BEFORE inserting (see ReferenceNumberUniquenessChecker) rather than
 * assuming this alone guarantees uniqueness.
 */
@Component
public class ReferenceNumberGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Clock clock;

    public ReferenceNumberGenerator(Clock clock) {
        this.clock = clock;
    }

    public String generate() {
        int year = clock.instant().atZone(ZoneOffset.UTC).getYear();
        int number = RANDOM.nextInt(1_000_000);
        return "JDW-%d-%06d".formatted(year, number);
    }
}
