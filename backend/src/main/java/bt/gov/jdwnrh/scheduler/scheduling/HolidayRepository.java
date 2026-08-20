package bt.gov.jdwnrh.scheduler.scheduling;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    @Query("SELECT h FROM Holiday h WHERE h.holidayDate = :date AND (h.departmentId IS NULL OR h.departmentId = :departmentId)")
    List<Holiday> findApplicable(@Param("date") LocalDate date, @Param("departmentId") UUID departmentId);

    /** For the admin console: a department's own holidays plus hospital-wide ones, in one list. */
    List<Holiday> findByDepartmentIdOrDepartmentIdIsNullOrderByHolidayDate(UUID departmentId);
}
