package bt.gov.jdwnrh.scheduler.scheduling;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bt.gov.jdwnrh.scheduler.appointment.Appointment;
import bt.gov.jdwnrh.scheduler.appointment.AppointmentLifecycleService;
import bt.gov.jdwnrh.scheduler.appointment.AppointmentNotFoundException;
import bt.gov.jdwnrh.scheduler.appointment.AppointmentQueryService;
import bt.gov.jdwnrh.scheduler.appointment.AppointmentResponse;
import bt.gov.jdwnrh.scheduler.appointment.AppointmentStatus;
import bt.gov.jdwnrh.scheduler.appointment.AppointmentWithPatientResponse;
import bt.gov.jdwnrh.scheduler.appointment.InvalidStatusTransitionException;
import bt.gov.jdwnrh.scheduler.config.CurrentUser;

/**
 * A doctor's own view of their day and their own schedule — everything here
 * is scoped to "my appointments" / "my leave" by RLS (appointment_doctor_own,
 * schedule_exception_doctor_own; see SecurityConfig for the hasRole("DOCTOR")
 * gate on this whole path).
 */
@RestController
@RequestMapping("/api/doctor-portal")
public class DoctorPortalController {

    private final AppointmentQueryService appointmentQueryService;
    private final AppointmentLifecycleService appointmentLifecycleService;
    private final ScheduleManagementService scheduleManagementService;
    private final DoctorRepository doctorRepository;
    private final CurrentUser currentUser;

    public DoctorPortalController(AppointmentQueryService appointmentQueryService,
                                   AppointmentLifecycleService appointmentLifecycleService,
                                   ScheduleManagementService scheduleManagementService,
                                   DoctorRepository doctorRepository, CurrentUser currentUser) {
        this.appointmentQueryService = appointmentQueryService;
        this.appointmentLifecycleService = appointmentLifecycleService;
        this.scheduleManagementService = scheduleManagementService;
        this.doctorRepository = doctorRepository;
        this.currentUser = currentUser;
    }

    @GetMapping("/appointments")
    public List<AppointmentWithPatientResponse> myDay(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return appointmentQueryService.listForDayWithPatientNames(date);
    }

    @PatchMapping("/appointments/{id}/start-consultation")
    public ResponseEntity<AppointmentResponse> startConsultation(@PathVariable UUID id) {
        Appointment appointment = appointmentLifecycleService.transition(id, AppointmentStatus.IN_CONSULTATION, "Consultation started");
        return ResponseEntity.ok(AppointmentResponse.from(appointment));
    }

    @PatchMapping("/appointments/{id}/complete")
    public ResponseEntity<AppointmentResponse> complete(@PathVariable UUID id) {
        Appointment appointment = appointmentLifecycleService.transition(id, AppointmentStatus.COMPLETED, "Consultation completed");
        return ResponseEntity.ok(AppointmentResponse.from(appointment));
    }

    @GetMapping("/schedule")
    public List<ScheduleBlockResponse> mySchedule() {
        UUID doctorId = myDoctorId();
        return scheduleManagementService.listScheduleBlocks(doctorId).stream()
                .map(ScheduleBlockResponse::from).toList();
    }

    @GetMapping("/exceptions")
    public List<ExceptionResponse> myExceptions() {
        UUID doctorId = myDoctorId();
        return scheduleManagementService.listExceptions(doctorId).stream()
                .map(e -> new ExceptionResponse(e.id(), e.start(), e.end(), e.reason())).toList();
    }

    @PostMapping("/exceptions")
    public ResponseEntity<ExceptionResponse> addException(@Valid @RequestBody CreateExceptionRequest request) {
        UUID doctorId = myDoctorId();
        var created = scheduleManagementService.addException(doctorId, request.start(), request.end(), request.reason());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ExceptionResponse(created.id(), created.start(), created.end(), created.reason()));
    }

    @DeleteMapping("/exceptions/{id}")
    public ResponseEntity<Void> removeException(@PathVariable UUID id) {
        scheduleManagementService.removeException(id);
        return ResponseEntity.noContent().build();
    }

    private UUID myDoctorId() {
        return doctorRepository.findByUserId(currentUser.require().userId())
                .orElseThrow(() -> new IllegalStateException("No doctor profile linked to this account"))
                .getId();
    }

    @ExceptionHandler(AppointmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(AppointmentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    public record ErrorResponse(String message) {
    }

    public record ScheduleBlockResponse(UUID id, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        static ScheduleBlockResponse from(DoctorSchedule s) {
            return new ScheduleBlockResponse(s.getId(), s.getDayOfWeek(), s.getStartTime(), s.getEndTime());
        }
    }

    public record ExceptionResponse(UUID id, Instant start, Instant end, String reason) {
    }

    public record CreateExceptionRequest(@NotNull Instant start, @NotNull Instant end, @NotBlank String reason) {
    }
}
