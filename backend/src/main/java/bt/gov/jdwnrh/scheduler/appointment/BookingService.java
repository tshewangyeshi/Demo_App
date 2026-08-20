package bt.gov.jdwnrh.scheduler.appointment;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import bt.gov.jdwnrh.scheduler.config.CurrentUser;
import bt.gov.jdwnrh.scheduler.config.RlsSessionInitializer;
import bt.gov.jdwnrh.scheduler.department.AppointmentType;
import bt.gov.jdwnrh.scheduler.department.AppointmentTypeRepository;
import bt.gov.jdwnrh.scheduler.iam.AppUser;
import bt.gov.jdwnrh.scheduler.iam.AppUserRepository;
import bt.gov.jdwnrh.scheduler.notification.NotificationEventType;
import bt.gov.jdwnrh.scheduler.notification.NotificationOutbox;
import bt.gov.jdwnrh.scheduler.notification.NotificationOutboxRepository;
import bt.gov.jdwnrh.scheduler.scheduling.Doctor;
import bt.gov.jdwnrh.scheduler.scheduling.DoctorRepository;
import bt.gov.jdwnrh.scheduler.scheduling.Slot;
import bt.gov.jdwnrh.scheduler.scheduling.SlotGenerationService;

/**
 * The design's centerpiece: booking is transactional, protected by the
 * Postgres exclusion constraint (see V2/V4), and the RLS context is applied
 * as the very first statement — see docs/designs/jdwnrh-scheduler.md,
 * "Data & Correctness Architecture".
 *
 * Two layers of "is this slot actually available" on purpose:
 * 1. Re-check against SlotGenerationService BEFORE inserting — the
 *    exclusion constraint alone only prevents overlap with EXISTING
 *    bookings; it says nothing about working hours, exceptions, or
 *    holidays, so a direct API call could otherwise book 3am on a holiday.
 * 2. The exclusion constraint itself, as the actual concurrency guarantee —
 *    step 1's check has a race window between "we looked" and "we insert",
 *    which only a DB constraint can close.
 */
@Service
public class BookingService {

    private final RlsSessionInitializer rlsSessionInitializer;
    private final CurrentUser currentUser;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final DoctorRepository doctorRepository;
    private final AppUserRepository appUserRepository;
    private final SlotGenerationService slotGenerationService;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final ReferenceNumberGenerator referenceNumberGenerator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BookingService(RlsSessionInitializer rlsSessionInitializer, CurrentUser currentUser,
                           AppointmentTypeRepository appointmentTypeRepository, DoctorRepository doctorRepository,
                           AppUserRepository appUserRepository, SlotGenerationService slotGenerationService,
                           AppointmentRepository appointmentRepository,
                           AppointmentHistoryRepository appointmentHistoryRepository,
                           NotificationOutboxRepository notificationOutboxRepository,
                           ReferenceNumberGenerator referenceNumberGenerator, ObjectMapper objectMapper, Clock clock) {
        this.rlsSessionInitializer = rlsSessionInitializer;
        this.currentUser = currentUser;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.doctorRepository = doctorRepository;
        this.appUserRepository = appUserRepository;
        this.slotGenerationService = slotGenerationService;
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.referenceNumberGenerator = referenceNumberGenerator;
        this.objectMapper = objectMapper;
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

        Instant requestedEnd = requestedStart.plus(appointmentType.slotFootprint());
        boolean actuallyAvailable = slotGenerationService
                .generateSlots(doctorId, doctor.getDepartmentId(), requestedStart.atZone(java.time.ZoneId.of("Asia/Thimphu")).toLocalDate(), appointmentType)
                .stream()
                .anyMatch(slot -> slot.start().equals(requestedStart) && slot.end().equals(requestedEnd));

        if (!actuallyAvailable) {
            throw new SlotUnavailableException("This slot is no longer available. Please pick another time.");
        }

        Appointment appointment = new Appointment(
                UUID.randomUUID(), referenceNumberGenerator.generate(), caller.userId(), doctorId,
                appointmentTypeId, requestedStart);
        appointment.transitionTo(AppointmentStatus.CONFIRMED);

        try {
            appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            // Constraint violation = correct rejection (the concurrent-booking
            // load test's expected outcome), not a bug — see design doc
            // Success Criteria. Anything else propagates as a real error.
            if (isExclusionViolation(ex)) {
                throw new SlotUnavailableException(
                        "This slot was just booked by someone else. Please pick another time.");
            }
            throw ex;
        }

        appointmentHistoryRepository.save(new AppointmentHistory(
                UUID.randomUUID(), appointment.getId(), null, AppointmentStatus.CONFIRMED,
                caller.userId(), clock.instant(), "Booked by patient"));

        enqueueConfirmationNotification(appointment);

        return appointment;
    }

    private void enqueueConfirmationNotification(Appointment appointment) {
        AppUser patient = appUserRepository.findById(appointment.getPatientId())
                .orElseThrow(() -> new IllegalStateException("Patient disappeared mid-transaction"));

        Map<String, Object> payload = Map.of(
                "referenceNumber", appointment.getReferenceNumber(),
                "doctorId", appointment.getDoctorId().toString(),
                "startTime", appointment.getStartTime().toString());

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize notification payload", e);
        }

        notificationOutboxRepository.save(new NotificationOutbox(
                UUID.randomUUID(), appointment.getId(), NotificationEventType.BOOKING_CONFIRMED,
                patient.getEmail(), payloadJson));
    }

    private static final String SQLSTATE_EXCLUSION_VIOLATION = "23P01";

    private static boolean isExclusionViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        return cause instanceof org.postgresql.util.PSQLException psqlException
                && SQLSTATE_EXCLUSION_VIOLATION.equals(psqlException.getSQLState());
    }
}
