package bt.gov.jdwnrh.scheduler.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;

import org.springframework.stereotype.Repository;

/**
 * Calls claim_24h_appointment_reminders / claim_2h_appointment_reminders
 * (see V18) — the SECURITY DEFINER functions that atomically claim
 * (UPDATE ... FOR UPDATE SKIP LOCKED) appointments needing a reminder and
 * flip their reminded_24h/reminded_2h flag in the same statement. Caller
 * must run this inside a transaction (see ReminderJob) — the claim's
 * exclusivity guarantee only holds for the duration of that transaction.
 */
@Repository
public class ReminderClaimRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<ReminderCandidate> claim24hReminders(int batchSize) {
        return claim("SELECT * FROM claim_24h_appointment_reminders(:batchSize)", batchSize);
    }

    public List<ReminderCandidate> claim2hReminders(int batchSize) {
        return claim("SELECT * FROM claim_2h_appointment_reminders(:batchSize)", batchSize);
    }

    @SuppressWarnings("unchecked")
    private List<ReminderCandidate> claim(String sql, int batchSize) {
        var rows = (List<Tuple>) entityManager.createNativeQuery(sql, Tuple.class)
                .setParameter("batchSize", batchSize)
                .getResultList();

        return rows.stream()
                .map(row -> new ReminderCandidate(
                        (UUID) row.get("appointment_id"),
                        (UUID) row.get("doctor_id"),
                        toInstant(row.get("start_time")),
                        (String) row.get("reference_number"),
                        (String) row.get("recipient_email")))
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

    public record ReminderCandidate(UUID appointmentId, UUID doctorId, Instant startTime, String referenceNumber, String recipientEmail) {
    }
}
