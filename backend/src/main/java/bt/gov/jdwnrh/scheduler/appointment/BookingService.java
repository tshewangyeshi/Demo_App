package bt.gov.jdwnrh.scheduler.appointment;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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
 * The design's centerpiece: booking is transactional, protected by the
 * Postgres exclusion constraint (see V2/V4), and the RLS context is applied
 * as the very first statement — see docs/designs/jdwnrh-scheduler.md,
 * "Data & Correctness Architecture".
 *
 * Two layers of "is this slot actually available" on purpose (see
 * SlotAvailabilityChecker for why the pre-check alone isn't enough).
 */
@Service
public class BookingService {

    private static final String SQLSTATE_EXCLUSION_VIOLATION = "23P01";
    private static final int MAX_REFERENCE_NUMBER_ATTEMPTS = 3;

    private final RlsSessionInitializer rlsSessionInitializer;
    private final CurrentUser currentUser;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final DoctorRepository doctorRepository;
    private final AppUserRepository appUserRepository;
    private final SlotAvailabilityChecker slotAvailabilityChecker;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final NotificationEnqueuer notificationEnqueuer;
    private final ReferenceNumberGenerator referenceNumberGenerator;
    private final ReferenceNumberUniquenessChecker referenceNumberUniquenessChecker;
    private final Clock clock;

    public BookingService(RlsSessionInitializer rlsSessionInitializer, CurrentUser currentUser,
                           AppointmentTypeRepository appointmentTypeRepository, DoctorRepository doctorRepository,
                           AppUserRepository appUserRepository, SlotAvailabilityChecker slotAvailabilityChecker,
                           AppointmentRepository appointmentRepository,
                           AppointmentHistoryRepository appointmentHistoryRepository,
                           NotificationEnqueuer notificationEnqueuer,
                           ReferenceNumberGenerator referenceNumberGenerator,
                           ReferenceNumberUniquenessChecker referenceNumberUniquenessChecker, Clock clock) {
        this.rlsSessionInitializer = rlsSessionInitializer;
        this.currentUser = currentUser;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.doctorRepository = doctorRepository;
        this.appUserRepository = appUserRepository;
        this.slotAvailabilityChecker = slotAvailabilityChecker;
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.notificationEnqueuer = notificationEnqueuer;
        this.referenceNumberGenerator = referenceNumberGenerator;
        this.referenceNumberUniquenessChecker = referenceNumberUniquenessChecker;
        this.clock = clock;
    }

    @Transactional
    public Appointment book(UUID doctorId, UUID appointmentTypeId, Instant requestedStart) {
        var caller = currentUser.require();
        rlsSessionInitializer.applyCurrentContext(caller);

        AppointmentType appointmentType = appointmentTypeRepository.findById(appointmentTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown appointment type: " + appointmentTypeId));
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown doctor: " + doctorId));

        if (!slotAvailabilityChecker.isAvailable(doctorId, doctor.getDepartmentId(), appointmentType, requestedStart)) {
            throw new SlotUnavailableException("This slot is no longer available. Please pick another time.");
        }

        Instant now = clock.instant();

        // ReferenceNumberGenerator's 6-digit random suffix makes a collision
        // astronomically unlikely per booking (~1 in a million), but "unlikely"
        // isn't "impossible". Checked BEFORE the insert, not retried after a
        // failed one: Postgres aborts the whole transaction on any statement
        // error until rollback, so a second saveAndFlush attempt inside the
        // same @Transactional method after a constraint violation would just
        // fail immediately with "current transaction is aborted" — retrying
        // in place here isn't actually possible, only avoiding the collision
        // in the first place is.
        String referenceNumber = referenceNumberGenerator.generate();
        for (int attempt = 1; attempt < MAX_REFERENCE_NUMBER_ATTEMPTS
                && referenceNumberUniquenessChecker.isActiveReferenceNumber(referenceNumber);
                attempt++) {
            referenceNumber = referenceNumberGenerator.generate();
        }

        Appointment appointment = new Appointment(
                UUID.randomUUID(), referenceNumber, caller.userId(), doctorId,
                appointmentTypeId, requestedStart, now);
        appointment.transitionTo(AppointmentStatus.CONFIRMED, now);

        try {
            appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            if (isExclusionViolation(ex)) {
                throw new SlotUnavailableException(
                        "This slot was just booked by someone else. Please pick another time.");
            }
            throw ex;
        }

        appointmentHistoryRepository.save(new AppointmentHistory(
                UUID.randomUUID(), appointment.getId(), null, AppointmentStatus.CONFIRMED,
                caller.userId(), now, "Booked by patient"));

        AppUser patient = appUserRepository.findById(appointment.getPatientId())
                .orElseThrow(() -> new IllegalStateException("Patient disappeared mid-transaction"));
        notificationEnqueuer.enqueue(NotificationEventType.BOOKING_CONFIRMED, appointment.getId(), patient.getEmail(),
                Map.of("referenceNumber", appointment.getReferenceNumber(),
                        "doctorId", appointment.getDoctorId().toString(),
                        "startTime", appointment.getStartTime().toString()));

        return appointment;
    }

    private static boolean isExclusionViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        return cause instanceof org.postgresql.util.PSQLException psqlException
                && SQLSTATE_EXCLUSION_VIOLATION.equals(psqlException.getSQLState());
    }
}
