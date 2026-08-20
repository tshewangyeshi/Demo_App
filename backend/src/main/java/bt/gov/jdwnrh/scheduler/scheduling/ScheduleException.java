package bt.gov.jdwnrh.scheduler.scheduling;

import java.time.Instant;
import java.util.UUID;

/**
 * Leave, training, blocked periods, etc. — overrides normal DoctorSchedule
 * availability. Not a JPA entity: the DB column is TSTZRANGE (same mapping
 * problem as Appointment.appointment_range), and nothing needs to query
 * this table with JPA criteria — SlotGenerationService reads it via a plain
 * native query returning start/end. See ScheduleExceptionRepository.
 */
public record ScheduleException(UUID id, UUID doctorId, Instant start, Instant end, String reason) {
}
