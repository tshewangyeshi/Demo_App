package bt.gov.jdwnrh.scheduler.appointment;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;

import org.springframework.stereotype.Repository;

/**
 * Calls get_visible_patient_names (see V15) — the RLS-safe way for staff/
 * doctor daily views to show a patient's name. Caller must have already
 * applied an RlsContext in the SAME transaction (SET LOCAL doesn't survive
 * past commit) — see AppointmentQueryService.listForDayWithPatientNames.
 */
@Repository
public class PatientNameRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<PatientName> findVisible() {
        var rows = (List<Tuple>) entityManager
                .createNativeQuery("SELECT * FROM get_visible_patient_names()", Tuple.class)
                .getResultList();

        return rows.stream()
                .map(row -> new PatientName((UUID) row.get("patient_id"), (String) row.get("first_name"), (String) row.get("last_name")))
                .toList();
    }

    public record PatientName(UUID patientId, String firstName, String lastName) {
    }
}
