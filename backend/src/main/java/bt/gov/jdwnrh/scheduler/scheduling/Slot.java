package bt.gov.jdwnrh.scheduler.scheduling;

import java.time.Instant;

/** An available (not yet booked, not blocked) slot. Always UTC — render in Asia/Thimphu at the presentation layer. */
public record Slot(Instant start, Instant end) {
}
