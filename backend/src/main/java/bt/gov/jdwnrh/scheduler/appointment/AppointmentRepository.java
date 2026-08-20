package bt.gov.jdwnrh.scheduler.appointment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * RLS-scoped (see V3__row_level_security.sql) — every call requires
 * RlsSessionInitializer.applyCurrentContext() to have already run in this
 * transaction, or these queries will silently return nothing (for a PATIENT)
 * rather than throw, since RLS filters rows, it doesn't error.
 */
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Optional<Appointment> findByReferenceNumberAndStatusIn(String referenceNumber, java.util.Collection<AppointmentStatus> statuses);

    /** Used by the staff daily queue and the doctor's own daily agenda — RLS narrows this to what the caller's role/department/ownership permits. */
    List<Appointment> findByStartTimeGreaterThanEqualAndStartTimeLessThan(Instant dayStart, Instant dayEnd, Sort sort);
}

