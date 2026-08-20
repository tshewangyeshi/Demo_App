package bt.gov.jdwnrh.scheduler.appointment;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bt.gov.jdwnrh.scheduler.config.CurrentUser;
import bt.gov.jdwnrh.scheduler.config.RlsSessionInitializer;
import bt.gov.jdwnrh.scheduler.department.AppointmentType;
import bt.gov.jdwnrh.scheduler.department.AppointmentTypeRepository;
import bt.gov.jdwnrh.scheduler.iam.AppUser;
import bt.gov.jdwnrh.scheduler.iam.AppUserRepository;
import bt.gov.jdwnrh.scheduler.notification.NotificationEnqueuer;
import bt.gov.jdwnrh.scheduler.notification.NotificationEventType;
import bt.gov.jdwnrh.scheduler.scheduling.Doctor;
import bt.gov.jdwnrh.scheduler.scheduling.DoctorRepository;

/**
 * Cancellation and reschedule. Both rely entirely on RLS for authorization —
 * findById() below only ever returns a row the caller's RlsContext permits,
 * so "not found" and "not yours to act on" are indistinguishable by design
 * (see AppointmentNotFoundException).
 */
@Service
public class AppointmentLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentLifecycleService.class);
    private static final String SQLSTATE_EXCLUSION_VIOLATION = "23P01";

    private final RlsSessionInitializer rlsSessionInitializer;
    private final CurrentUser currentUser;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final DoctorRepository doctorRepository;
    private final AppUserRepository appUserRepository;
    private final SlotAvailabilityChecker slotAvailabilityChecker;
    private final NotificationEnqueuer notificationEnqueuer;
    private final Clock clock;

    public AppointmentLifecycleService(RlsSessionInitializer rlsSessionInitializer, CurrentUser currentUser,
                                        AppointmentRepository appointmentRepository,
                                        AppointmentHistoryRepository appointmentHistoryRepository,
                                        AppointmentTypeRepository appointmentTypeRepository,
                                        DoctorRepository doctorRepository, AppUserRepository appUserRepository,
                                        SlotAvailabilityChecker slotAvailabilityChecker,
                                        NotificationEnqueuer notificationEnqueuer, Clock clock) {
        this.rlsSessionInitializer = rlsSessionInitializer;
        this.currentUser = currentUser;
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.doctorRepository = doctorRepository;
        this.appUserRepository = appUserRepository;
        this.slotAvailabilityChecker = slotAvailabilityChecker;
        this.notificationEnqueuer = notificationEnqueuer;
        this.clock = clock;
    }

    @Transactional
    public Appointment cancel(UUID appointmentId) {
        var caller = currentUser.require();
        rlsSessionInitializer.applyCurrentContext(caller);

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException("Appointment not found: " + appointmentId));

        Instant now = clock.instant();
        AppointmentStatus previousStatus = appointment.getStatus();
        appointment.transitionTo(AppointmentStatus.CANCELLED, now); // frees the slot immediately — the partial exclusion
        appointmentRepository.save(appointment);                    // constraint no longer blocks this range once CANCELLED

        appointmentHistoryRepository.save(new AppointmentHistory(
                UUID.randomUUID(), appointment.getId(), previousStatus, AppointmentStatus.CANCELLED,
                caller.userId(), now, "Cancelled"));

        notifyPatient(appointment, NotificationEventType.CANCELLED, Map.of(
                "referenceNumber", appointment.getReferenceNumber()));

        log.info("Appointment cancelled appointmentId={} referenceNumber={} actorId={} previousStatus={}",
                appointment.getId(), appointment.getReferenceNumber(), caller.userId(), previousStatus);

        return appointment;
    }

    @Transactional
    public Appointment reschedule(UUID appointmentId, Instant newStartTime) {
        var caller = currentUser.require();
        rlsSessionInitializer.applyCurrentContext(caller);

        Appointment original = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException("Appointment not found: " + appointmentId));

        AppointmentType appointmentType = appointmentTypeRepository.findById(original.getAppointmentTypeId())
                .orElseThrow(() -> new IllegalStateException("Appointment type disappeared: " + original.getAppointmentTypeId()));
        Doctor doctor = doctorRepository.findById(original.getDoctorId())
                .orElseThrow(() -> new IllegalStateException("Doctor disappeared: " + original.getDoctorId()));

        if (!slotAvailabilityChecker.isAvailable(original.getDoctorId(), doctor.getDepartmentId(), appointmentType, newStartTime)) {
            log.info("Reschedule rejected: slot unavailable at pre-check appointmentId={} actorId={} newStartTime={}",
                    appointmentId, caller.userId(), newStartTime);
            throw new SlotUnavailableException("That time is not available. Please pick another slot.");
        }

        Instant now = clock.instant();
        AppointmentStatus previousStatus = original.getStatus();
        original.transitionTo(AppointmentStatus.RESCHEDULED, now); // terminal — drops out of the partial exclusion index, freeing its range
        // MUST flush here, not just save(): Hibernate's default action-queue
        // order runs ALL pending inserts before ALL pending updates within a
        // single flush, regardless of call order — so without forcing this
        // UPDATE to hit the DB now, the saveAndFlush(rescheduled) INSERT below
        // (same reference_number, carried forward unchanged) would execute
        // while `original` is still ACTIVE in the database, tripping
        // uq_appointment_reference_active. Only found by actually running a
        // reschedule end-to-end, not by reading the code.
        appointmentRepository.saveAndFlush(original);

        appointmentHistoryRepository.save(new AppointmentHistory(
                UUID.randomUUID(), original.getId(), previousStatus, AppointmentStatus.RESCHEDULED,
                caller.userId(), now, "Rescheduled to a new appointment"));

        // Reference number carries forward UNCHANGED — see design doc,
        // "reference_number is not a plain-unique column": the partial
        // unique index allows this because the old row is no longer ACTIVE.
        Appointment rescheduled = new Appointment(
                UUID.randomUUID(), original.getReferenceNumber(), original.getPatientId(), original.getDoctorId(),
                original.getAppointmentTypeId(), newStartTime, now);
        rescheduled.setRescheduledFromId(original.getId());
        rescheduled.transitionTo(AppointmentStatus.CONFIRMED, now);

        try {
            appointmentRepository.saveAndFlush(rescheduled);
        } catch (DataIntegrityViolationException ex) {
            if (isExclusionViolation(ex)) {
                log.info("Reschedule rejected: exclusion constraint (concurrent race lost) appointmentId={} actorId={} newStartTime={}",
                        appointmentId, caller.userId(), newStartTime);
                throw new SlotUnavailableException("That time was just booked by someone else. Please pick another slot.");
            }
            throw ex;
        }

        appointmentHistoryRepository.save(new AppointmentHistory(
                UUID.randomUUID(), rescheduled.getId(), null, AppointmentStatus.CONFIRMED,
                caller.userId(), now, "Rescheduled from " + original.getId()));

        notifyPatient(rescheduled, NotificationEventType.RESCHEDULED, Map.of(
                "referenceNumber", rescheduled.getReferenceNumber(),
                "newStartTime", rescheduled.getStartTime().toString()));

        log.info("Appointment rescheduled fromAppointmentId={} toAppointmentId={} referenceNumber={} actorId={} newStartTime={}",
                original.getId(), rescheduled.getId(), rescheduled.getReferenceNumber(), caller.userId(), newStartTime);

        return rescheduled;
    }

    /**
     * The staff/doctor workflow (CHECKED_IN -> WAITING -> IN_CONSULTATION ->
     * COMPLETED, or CONFIRMED -> NO_SHOW) — same RLS-scoped lookup and
     * enforced state machine as cancel/reschedule, just without the
     * cancellation-specific slot-freeing or patient notification.
     */
    @Transactional
    public Appointment transition(UUID appointmentId, AppointmentStatus target, String note) {
        var caller = currentUser.require();
        rlsSessionInitializer.applyCurrentContext(caller);

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException("Appointment not found: " + appointmentId));

        Instant now = clock.instant();
        AppointmentStatus previousStatus = appointment.getStatus();
        appointment.transitionTo(target, now);
        appointmentRepository.save(appointment);

        appointmentHistoryRepository.save(new AppointmentHistory(
                UUID.randomUUID(), appointment.getId(), previousStatus, target, caller.userId(), now, note));

        return appointment;
    }

    private void notifyPatient(Appointment appointment, NotificationEventType eventType, Map<String, Object> extra) {
        AppUser patient = appUserRepository.findById(appointment.getPatientId())
                .orElseThrow(() -> new IllegalStateException("Patient disappeared: " + appointment.getPatientId()));
        notificationEnqueuer.enqueue(eventType, appointment.getId(), patient.getEmail(), extra);
    }

    private static boolean isExclusionViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        return cause instanceof org.postgresql.util.PSQLException psqlException
                && SQLSTATE_EXCLUSION_VIOLATION.equals(psqlException.getSQLState());
    }
}
