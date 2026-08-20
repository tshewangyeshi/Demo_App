package bt.gov.jdwnrh.scheduler.appointment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * RLS-scoped (see V3__row_level_security.sql) — every call requires
 * RlsSessionInitializer.applyCurrentContext() to have already run in this
 * transaction, or these queries will silently return nothing (for a PATIENT)
 * rather than throw, since RLS filters rows, it doesn't error.
 */
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Optional<Appointment> findByReferenceNumberAndStatusIn(String referenceNumber, java.util.Collection<AppointmentStatus> statuses);
}
