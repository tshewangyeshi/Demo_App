package bt.gov.jdwnrh.scheduler.scheduling;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads are effectively public (see V9__doctor_schedule_public_read.sql) —
 * no RlsContext needs to be set before calling this for a browsing patient.
 * Writes (not yet implemented) remain admin/department-admin scoped by V3's
 * INSERT policy.
 */
public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, UUID> {

    List<DoctorSchedule> findByDoctorId(UUID doctorId);
}
