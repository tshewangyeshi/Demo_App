package bt.gov.jdwnrh.scheduler.admin;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bt.gov.jdwnrh.scheduler.audit.AuditLogger;
import bt.gov.jdwnrh.scheduler.config.RlsContext;
import bt.gov.jdwnrh.scheduler.department.AppointmentType;
import bt.gov.jdwnrh.scheduler.department.AppointmentTypeRepository;
import bt.gov.jdwnrh.scheduler.department.Department;
import bt.gov.jdwnrh.scheduler.department.DepartmentRepository;
import bt.gov.jdwnrh.scheduler.department.Specialty;
import bt.gov.jdwnrh.scheduler.department.SpecialtyRepository;
import bt.gov.jdwnrh.scheduler.scheduling.Holiday;
import bt.gov.jdwnrh.scheduler.scheduling.HolidayRepository;

/**
 * Admin write endpoints for the hierarchical reference data patients browse
 * (department -> specialty -> appointment type) plus holidays. Reads stay on
 * the public DepartmentController/HolidayRepository — this is writes only.
 * None of these tables carry RLS (see AdminScope's javadoc), so AdminScope is
 * the actual gate. Every write also logs to AuditLog (see MVP Scope's
 * "Admin ... audit logs").
 */
@RestController
@RequestMapping("/api/admin")
public class AdminReferenceDataController {

    private final AdminScope adminScope;
    private final AuditLogger auditLogger;
    private final DepartmentRepository departmentRepository;
    private final SpecialtyRepository specialtyRepository;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final HolidayRepository holidayRepository;
    private final Clock clock;

    public AdminReferenceDataController(AdminScope adminScope, AuditLogger auditLogger, DepartmentRepository departmentRepository,
                                         SpecialtyRepository specialtyRepository,
                                         AppointmentTypeRepository appointmentTypeRepository,
                                         HolidayRepository holidayRepository, Clock clock) {
        this.adminScope = adminScope;
        this.auditLogger = auditLogger;
        this.departmentRepository = departmentRepository;
        this.specialtyRepository = specialtyRepository;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.holidayRepository = holidayRepository;
        this.clock = clock;
    }

    // --- Department: hospital-wide, only HOSPITAL_ADMIN/SUPER_ADMIN ---

    @PostMapping("/departments")
    public ResponseEntity<Department> createDepartment(@Valid @RequestBody NameRequest request) {
        RlsContext caller = adminScope.require();
        adminScope.requireHospitalWide(caller);
        Department department = departmentRepository.save(new Department(UUID.randomUUID(), request.name(), clock.instant()));
        auditLogger.log(caller.userId(), "CREATE", "DEPARTMENT", department.getId(), null, Map.of("name", department.getName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(department);
    }

    @PatchMapping("/departments/{id}")
    public ResponseEntity<Department> renameDepartment(@PathVariable UUID id, @Valid @RequestBody NameRequest request) {
        RlsContext caller = adminScope.require();
        adminScope.requireHospitalWide(caller);
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown department: " + id));
        String previousName = department.getName();
        department.setName(request.name());
        departmentRepository.save(department);
        auditLogger.log(caller.userId(), "UPDATE", "DEPARTMENT", id, Map.of("name", previousName), Map.of("name", request.name()));
        return ResponseEntity.ok(department);
    }

    @DeleteMapping("/departments/{id}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable UUID id) {
        RlsContext caller = adminScope.require();
        adminScope.requireHospitalWide(caller);
        Department department = departmentRepository.findById(id).orElse(null);
        departmentRepository.deleteById(id);
        auditLogger.log(caller.userId(), "DELETE", "DEPARTMENT", id,
                department != null ? Map.of("name", department.getName()) : null, null);
        return ResponseEntity.noContent().build();
    }

    // --- Specialty: department-scoped ---

    @PostMapping("/specialties")
    public ResponseEntity<Specialty> createSpecialty(@Valid @RequestBody CreateSpecialtyRequest request) {
        RlsContext caller = adminScope.require();
        adminScope.requireDepartmentAccess(caller, request.departmentId());
        Specialty specialty = specialtyRepository.save(
                new Specialty(UUID.randomUUID(), request.departmentId(), request.name(), clock.instant()));
        auditLogger.log(caller.userId(), "CREATE", "SPECIALTY", specialty.getId(), null,
                Map.of("departmentId", request.departmentId().toString(), "name", request.name()));
        return ResponseEntity.status(HttpStatus.CREATED).body(specialty);
    }

    @PatchMapping("/specialties/{id}")
    public ResponseEntity<Specialty> renameSpecialty(@PathVariable UUID id, @Valid @RequestBody NameRequest request) {
        RlsContext caller = adminScope.require();
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown specialty: " + id));
        adminScope.requireDepartmentAccess(caller, specialty.getDepartmentId());
        String previousName = specialty.getName();
        specialty.setName(request.name());
        specialtyRepository.save(specialty);
        auditLogger.log(caller.userId(), "UPDATE", "SPECIALTY", id, Map.of("name", previousName), Map.of("name", request.name()));
        return ResponseEntity.ok(specialty);
    }

    @DeleteMapping("/specialties/{id}")
    public ResponseEntity<Void> deleteSpecialty(@PathVariable UUID id) {
        RlsContext caller = adminScope.require();
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown specialty: " + id));
        adminScope.requireDepartmentAccess(caller, specialty.getDepartmentId());
        specialtyRepository.deleteById(id);
        auditLogger.log(caller.userId(), "DELETE", "SPECIALTY", id, Map.of("name", specialty.getName()), null);
        return ResponseEntity.noContent().build();
    }

    // --- Appointment type: scoped via its specialty's department ---

    @PostMapping("/appointment-types")
    public ResponseEntity<AppointmentType> createAppointmentType(@Valid @RequestBody CreateAppointmentTypeRequest request) {
        RlsContext caller = adminScope.require();
        Specialty specialty = specialtyRepository.findById(request.specialtyId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown specialty: " + request.specialtyId()));
        adminScope.requireDepartmentAccess(caller, specialty.getDepartmentId());
        AppointmentType type = appointmentTypeRepository.save(new AppointmentType(
                UUID.randomUUID(), request.specialtyId(), request.name(),
                request.durationMinutes(), request.bufferMinutes(), clock.instant()));
        auditLogger.log(caller.userId(), "CREATE", "APPOINTMENT_TYPE", type.getId(), null, Map.of(
                "specialtyId", request.specialtyId().toString(), "name", request.name(),
                "durationMinutes", request.durationMinutes(), "bufferMinutes", request.bufferMinutes()));
        return ResponseEntity.status(HttpStatus.CREATED).body(type);
    }

    @PatchMapping("/appointment-types/{id}")
    public ResponseEntity<AppointmentType> updateAppointmentType(@PathVariable UUID id,
                                                                   @Valid @RequestBody CreateAppointmentTypeRequest request) {
        RlsContext caller = adminScope.require();
        AppointmentType type = appointmentTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown appointment type: " + id));
        Specialty specialty = specialtyRepository.findById(type.getSpecialtyId())
                .orElseThrow(() -> new IllegalStateException("Specialty disappeared: " + type.getSpecialtyId()));
        adminScope.requireDepartmentAccess(caller, specialty.getDepartmentId());
        Map<String, Object> previous = Map.of("name", type.getName(),
                "durationMinutes", type.getDurationMinutes(), "bufferMinutes", type.getBufferMinutes());
        type.setName(request.name());
        type.setDurationMinutes(request.durationMinutes());
        type.setBufferMinutes(request.bufferMinutes());
        appointmentTypeRepository.save(type);
        auditLogger.log(caller.userId(), "UPDATE", "APPOINTMENT_TYPE", id, previous, Map.of(
                "name", request.name(), "durationMinutes", request.durationMinutes(), "bufferMinutes", request.bufferMinutes()));
        return ResponseEntity.ok(type);
    }

    @DeleteMapping("/appointment-types/{id}")
    public ResponseEntity<Void> deleteAppointmentType(@PathVariable UUID id) {
        RlsContext caller = adminScope.require();
        AppointmentType type = appointmentTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown appointment type: " + id));
        Specialty specialty = specialtyRepository.findById(type.getSpecialtyId())
                .orElseThrow(() -> new IllegalStateException("Specialty disappeared: " + type.getSpecialtyId()));
        adminScope.requireDepartmentAccess(caller, specialty.getDepartmentId());
        appointmentTypeRepository.deleteById(id);
        auditLogger.log(caller.userId(), "DELETE", "APPOINTMENT_TYPE", id, Map.of("name", type.getName()), null);
        return ResponseEntity.noContent().build();
    }

    // --- Holiday: department-scoped, or hospital-wide (departmentId null) for HOSPITAL_ADMIN/SUPER_ADMIN ---

    @GetMapping("/holidays")
    public List<Holiday> listHolidays(@RequestParam UUID departmentId) {
        adminScope.requireDepartmentAccess(adminScope.require(), departmentId);
        return holidayRepository.findByDepartmentIdOrDepartmentIdIsNullOrderByHolidayDate(departmentId);
    }

    @PostMapping("/holidays")
    public ResponseEntity<Holiday> createHoliday(@Valid @RequestBody CreateHolidayRequest request) {
        RlsContext caller = adminScope.require();
        if (request.departmentId() == null) {
            adminScope.requireHospitalWide(caller); // hospital-wide holiday
        } else {
            adminScope.requireDepartmentAccess(caller, request.departmentId());
        }
        Holiday holiday = holidayRepository.save(new Holiday(
                UUID.randomUUID(), request.departmentId(), request.holidayDate(), request.name(), clock.instant()));
        auditLogger.log(caller.userId(), "CREATE", "HOLIDAY", holiday.getId(), null, Map.of(
                "departmentId", request.departmentId() != null ? request.departmentId().toString() : "hospital-wide",
                "holidayDate", request.holidayDate().toString(), "name", request.name()));
        return ResponseEntity.status(HttpStatus.CREATED).body(holiday);
    }

    @DeleteMapping("/holidays/{id}")
    public ResponseEntity<Void> deleteHoliday(@PathVariable UUID id) {
        RlsContext caller = adminScope.require();
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown holiday: " + id));
        if (holiday.getDepartmentId() == null) {
            adminScope.requireHospitalWide(caller);
        } else {
            adminScope.requireDepartmentAccess(caller, holiday.getDepartmentId());
        }
        holidayRepository.deleteById(id);
        auditLogger.log(caller.userId(), "DELETE", "HOLIDAY", id,
                Map.of("holidayDate", holiday.getHolidayDate().toString(), "name", holiday.getName()), null);
        return ResponseEntity.noContent().build();
    }

    public record NameRequest(@NotBlank String name) {
    }

    public record CreateSpecialtyRequest(@NotNull UUID departmentId, @NotBlank String name) {
    }

    public record CreateAppointmentTypeRequest(@NotNull UUID specialtyId, @NotBlank String name,
                                                 @Min(1) int durationMinutes, @Min(0) int bufferMinutes) {
    }

    public record CreateHolidayRequest(UUID departmentId, @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate holidayDate,
                                        @NotBlank String name) {
    }
}
