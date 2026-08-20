package bt.gov.jdwnrh.scheduler.department;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialtyRepository extends JpaRepository<Specialty, UUID> {

    List<Specialty> findByDepartmentId(UUID departmentId);
}
