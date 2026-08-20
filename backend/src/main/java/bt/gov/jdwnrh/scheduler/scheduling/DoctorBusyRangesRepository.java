package bt.gov.jdwnrh.scheduler.scheduling;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;

import org.springframework.stereotype.Repository;

/** Calls get_doctor_busy_ranges (see V10) — the RLS-safe way to learn which ranges are booked, without exposing whose booking it is. */
@Repository
public class DoctorBusyRangesRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<BusyRange> findBusyRanges(UUID doctorId, Instant from, Instant to) {
        var rows = (List<Tuple>) entityManager
                .createNativeQuery("SELECT * FROM get_doctor_busy_ranges(:doctorId, :from, :to)", Tuple.class)
                .setParameter("doctorId", doctorId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        return rows.stream()
                .map(row -> new BusyRange(toInstant(row.get("start_time")), toInstant(row.get("end_time"))))
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

    public record BusyRange(Instant start, Instant end) {
        public boolean overlaps(Instant otherStart, Instant otherEnd) {
            return start.isBefore(otherEnd) && otherStart.isBefore(end);
        }
    }
}
