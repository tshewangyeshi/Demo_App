package bt.gov.jdwnrh.scheduler.appointment;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record BookAppointmentRequest(
        @NotNull UUID doctorId,
        @NotNull UUID appointmentTypeId,
        @NotNull Instant startTime) {
}
