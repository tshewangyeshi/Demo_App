package bt.gov.jdwnrh.scheduler.appointment;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public record RescheduleRequest(@NotNull Instant newStartTime) {
}
