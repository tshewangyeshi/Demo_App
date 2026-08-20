package bt.gov.jdwnrh.scheduler.scheduling;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;

import org.springframework.stereotype.Repository;

/**
 * exception_range is TSTZRANGE (no native Hibernate mapping — same reasoning
 * as Appointment.appointment_range), so this reads it via a native query
 * using Postgres's lower()/upper() range functions rather than a JPA entity.
 * Not RLS-protected — schedule_exception is reference data needed for
 * availability computation, same category as doctor_schedule.
 */
@Repository
public class ScheduleExceptionRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<ScheduleException> findOverlapping(UUID doctorId, Instant from, Instant to) {
        var rows = (List<Tuple>) entityManager.createNativeQuery(
                        """
                        SELECT id, doctor_id, lower(exception_range) AS start_time,
                               upper(exception_range) AS end_time, reason
                        FROM schedule_exception
                        WHERE doctor_id = :doctorId
                          AND exception_range && tstzrange(:from, :to, '[)')
                        """, Tuple.class)
                .setParameter("doctorId", doctorId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        return rows.stream()
                .map(row -> new ScheduleException(
                        (UUID) row.get("id"),
                        (UUID) row.get("doctor_id"),
                        toInstant(row.get("start_time")),
                        toInstant(row.get("end_time")),
                        (String) row.get("reason")))
                .toList();
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalStateException("Unexpected timestamp type: " + value.getClass());
    }
}
