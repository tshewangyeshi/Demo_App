package bt.gov.jdwnrh.scheduler.admin;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bt.gov.jdwnrh.scheduler.audit.AuditLogger;
import bt.gov.jdwnrh.scheduler.config.RlsContext;
import bt.gov.jdwnrh.scheduler.scheduling.Doctor;
import bt.gov.jdwnrh.scheduler.scheduling.DoctorRepository;
import bt.gov.jdwnrh.scheduler.scheduling.DoctorSchedule;
import bt.gov.jdwnrh.scheduler.scheduling.ScheduleException;
import bt.gov.jdwnrh.scheduler.scheduling.ScheduleManagementService;

/**
 * Admin management of a doctor's profile and calendar (weekly schedule
 * blocks + leave/blocked exceptions). Department-scoped like the rest of
 * /api/admin/** — a department admin can only manage doctors in their own
 * department; hospital/super admin, any doctor.
 */
@RestController
@RequestMapping("/api/admin/doctors")
public class AdminDoctorController {

    private final AdminScope adminScope;
    private final AuditLogger auditLogger;
    private final DoctorRepository doctorRepository;
    private final ScheduleManagementService scheduleManagementService;

    public AdminDoctorController(AdminScope adminScope, AuditLogger auditLogger, DoctorRepository doctorRepository,
                                  ScheduleManagementService scheduleManagementService) {
        this.adminScope = adminScope;
        this.auditLogger = auditLogger;
        this.doctorRepository = doctorRepository;
        this.scheduleManagementService = scheduleManagementService;
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Doctor> updateBio(@PathVariable UUID id, @Valid @RequestBody UpdateBioRequest request) {
        RlsContext caller = adminScope.require();
        Doctor doctor = requireScopedDoctor(caller, id);
        String previousBio = doctor.getBio();
        doctor.setBio(request.bio());
        doctorRepository.save(doctor);
        auditLogger.log(caller.userId(), "UPDATE", "DOCTOR", id, Map.of("bio", previousBio == null ? "" : previousBio),
                Map.of("bio", request.bio() == null ? "" : request.bio()));
        return ResponseEntity.ok(doctor);
    }

    @GetMapping("/{id}/schedule")
    public List<ScheduleBlockResponse> schedule(@PathVariable UUID id) {
        requireScopedDoctor(adminScope.require(), id);
        return scheduleManagementService.listScheduleBlocks(id).stream().map(ScheduleBlockResponse::from).toList();
    }

    @PostMapping("/{id}/schedule")
    public ResponseEntity<ScheduleBlockResponse> addScheduleBlock(@PathVariable UUID id,
                                                                    @Valid @RequestBody AddScheduleBlockRequest request) {
        RlsContext caller = adminScope.require();
        requireScopedDoctor(caller, id);
        DoctorSchedule block = scheduleManagementService.addScheduleBlock(
                id, request.dayOfWeek(), request.startTime(), request.endTime());
        auditLogger.log(caller.userId(), "CREATE", "DOCTOR_SCHEDULE", block.getId(), null, Map.of(
                "doctorId", id.toString(), "dayOfWeek", request.dayOfWeek().name(),
                "startTime", request.startTime().toString(), "endTime", request.endTime().toString()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ScheduleBlockResponse.from(block));
    }

    @DeleteMapping("/{id}/schedule/{scheduleId}")
    public ResponseEntity<Void> removeScheduleBlock(@PathVariable UUID id, @PathVariable UUID scheduleId) {
        RlsContext caller = adminScope.require();
        requireScopedDoctor(caller, id);
        scheduleManagementService.removeScheduleBlock(scheduleId);
        auditLogger.log(caller.userId(), "DELETE", "DOCTOR_SCHEDULE", scheduleId, Map.of("doctorId", id.toString()), null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/exceptions")
    public List<ExceptionResponse> exceptions(@PathVariable UUID id) {
        requireScopedDoctor(adminScope.require(), id);
        return scheduleManagementService.listExceptions(id).stream()
                .map(e -> new ExceptionResponse(e.id(), e.start(), e.end(), e.reason())).toList();
    }

    @PostMapping("/{id}/exceptions")
    public ResponseEntity<ExceptionResponse> addException(@PathVariable UUID id,
                                                            @Valid @RequestBody AddExceptionRequest request) {
        RlsContext caller = adminScope.require();
        requireScopedDoctor(caller, id);
        ScheduleException created = scheduleManagementService.addException(id, request.start(), request.end(), request.reason());
        auditLogger.log(caller.userId(), "CREATE", "DOCTOR_EXCEPTION", created.id(), null, Map.of(
                "doctorId", id.toString(), "start", request.start().toString(), "end", request.end().toString(), "reason", request.reason()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ExceptionResponse(created.id(), created.start(), created.end(), created.reason()));
    }

    @DeleteMapping("/{id}/exceptions/{exceptionId}")
    public ResponseEntity<Void> removeException(@PathVariable UUID id, @PathVariable UUID exceptionId) {
        RlsContext caller = adminScope.require();
        requireScopedDoctor(caller, id);
        scheduleManagementService.removeException(exceptionId);
        auditLogger.log(caller.userId(), "DELETE", "DOCTOR_EXCEPTION", exceptionId, Map.of("doctorId", id.toString()), null);
        return ResponseEntity.noContent().build();
    }

    private Doctor requireScopedDoctor(RlsContext caller, UUID doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown doctor: " + doctorId));
        adminScope.requireDepartmentAccess(caller, doctor.getDepartmentId());
        return doctor;
    }

    public record UpdateBioRequest(String bio) {
    }

    public record ScheduleBlockResponse(UUID id, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        static ScheduleBlockResponse from(DoctorSchedule s) {
            return new ScheduleBlockResponse(s.getId(), s.getDayOfWeek(), s.getStartTime(), s.getEndTime());
        }
    }

    public record AddScheduleBlockRequest(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime, @NotNull LocalTime endTime) {
    }

    public record ExceptionResponse(UUID id, Instant start, Instant end, String reason) {
    }

    public record AddExceptionRequest(@NotNull Instant start, @NotNull Instant end, @NotBlank String reason) {
    }
}
