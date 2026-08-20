package bt.gov.jdwnrh.scheduler.scheduling;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Doctor listings are public reference data (not RLS-protected) — same category as Department/Specialty. */
public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    List<Doctor> findByDepartmentId(UUID departmentId);
}
