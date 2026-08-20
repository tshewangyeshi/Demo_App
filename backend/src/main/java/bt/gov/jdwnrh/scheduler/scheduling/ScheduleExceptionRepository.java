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
 * Reads are public (see V14__admin_write_rls.sql, schedule_exception_public_read
 * — same public-read/gated-write shape as doctor_schedule); writes require an
 * applied RlsContext (admin/department-admin, or the doctor's own exceptions).
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

    @SuppressWarnings("unchecked")
    public List<ScheduleException> findByDoctorId(UUID doctorId) {
        var rows = (List<Tuple>) entityManager.createNativeQuery(
                        """
                        SELECT id, doctor_id, lower(exception_range) AS start_time,
                               upper(exception_range) AS end_time, reason
                        FROM schedule_exception
                        WHERE doctor_id = :doctorId
                        ORDER BY lower(exception_range)
                        """, Tuple.class)
                .setParameter("doctorId", doctorId)
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

    /** Caller must have already applied an RlsContext in this transaction — this INSERT is gated by V14's schedule_exception RLS policies. */
    public void insert(UUID id, UUID doctorId, Instant start, Instant end, String reason, Instant now) {
        entityManager.createNativeQuery(
                        """
                        INSERT INTO schedule_exception (id, doctor_id, exception_range, reason, created_at)
                        VALUES (:id, :doctorId, tstzrange(:start, :end, '[)'), :reason, :now)
                        """)
                .setParameter("id", id)
                .setParameter("doctorId", doctorId)
                .setParameter("start", start)
                .setParameter("end", end)
                .setParameter("reason", reason)
                .setParameter("now", now)
                .executeUpdate();
    }

    /** Returns the number of rows deleted (0 if the id doesn't exist or RLS filtered it out — indistinguishable, matching the rest of this design's RLS-as-authorization pattern). */
    public int deleteById(UUID id) {
        return entityManager.createNativeQuery("DELETE FROM schedule_exception WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();
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
