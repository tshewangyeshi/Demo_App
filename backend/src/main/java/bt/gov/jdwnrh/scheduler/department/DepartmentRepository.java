package bt.gov.jdwnrh.scheduler.department;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Public reference data — not RLS-protected (see SecurityConfig, /api/departments/** permitAll). */
public interface DepartmentRepository extends JpaRepository<Department, UUID> {
}
