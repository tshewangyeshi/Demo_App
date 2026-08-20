package bt.gov.jdwnrh.scheduler.scheduling;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;

import org.springframework.stereotype.Repository;

/** Calls get_doctor_public_profiles (see V12) — the RLS-safe way to read a doctor's display name for browsing. */
@Repository
public class DoctorProfileRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<DoctorProfile> findByDepartment(UUID departmentId) {
        var rows = (List<Tuple>) entityManager
                .createNativeQuery("SELECT * FROM get_doctor_public_profiles(:departmentId)", Tuple.class)
                .setParameter("departmentId", departmentId)
                .getResultList();

        return rows.stream()
                .map(row -> new DoctorProfile(
                        (UUID) row.get("doctor_id"),
                        (UUID) row.get("department_id"),
                        (String) row.get("first_name"),
                        (String) row.get("last_name"),
                        (String) row.get("bio")))
                .toList();
    }

    public record DoctorProfile(UUID doctorId, UUID departmentId, String firstName, String lastName, String bio) {
    }
}
