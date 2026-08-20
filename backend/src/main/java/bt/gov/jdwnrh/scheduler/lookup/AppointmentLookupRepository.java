package bt.gov.jdwnrh.scheduler.lookup;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;

import org.springframework.stereotype.Repository;

/** Calls lookup_appointment_by_reference (see V20) — the RLS-safe way to resolve a reference number for an unauthenticated caller. */
@Repository
public class AppointmentLookupRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public Optional<LookupResult> lookup(String referenceNumber, String lastName) {
        var rows = (List<Tuple>) entityManager
                .createNativeQuery("SELECT * FROM lookup_appointment_by_reference(:referenceNumber, :lastName)", Tuple.class)
                .setParameter("referenceNumber", referenceNumber)
                .setParameter("lastName", lastName)
                .getResultList();

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Tuple row = rows.get(0);
        return Optional.of(new LookupResult(
                (String) row.get("reference_number"),
                (String) row.get("status"),
                toInstant(row.get("start_time")),
                (String) row.get("doctor_first_name"),
                (String) row.get("doctor_last_name")));
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

    public record LookupResult(String referenceNumber, String status, Instant startTime, String doctorFirstName, String doctorLastName) {
    }
}
