package bt.gov.jdwnrh.scheduler.admin;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
 * the actual gate.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminReferenceDataController {

    private final AdminScope adminScope;
    private final DepartmentRepository departmentRepository;
    private final SpecialtyRepository specialtyRepository;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final HolidayRepository holidayRepository;
    private final Clock clock;

    public AdminReferenceDataController(AdminScope adminScope, DepartmentRepository departmentRepository,
                                         SpecialtyRepository specialtyRepository,
                                         AppointmentTypeRepository appointmentTypeRepository,
                                         HolidayRepository holidayRepository, Clock clock) {
        this.adminScope = adminScope;
        this.departmentRepository = departmentRepository;
        this.specialtyRepository = specialtyRepository;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.holidayRepository = holidayRepository;
        this.clock = clock;
    }

    // --- Department: hospital-wide, only HOSPITAL_ADMIN/SUPER_ADMIN ---

    @PostMapping("/departments")
    public ResponseEntity<Department> createDepartment(@Valid @RequestBody NameRequest request) {
        adminScope.requireHospitalWide(adminScope.require());
        Department department = departmentRepository.save(new Department(UUID.randomUUID(), request.name(), clock.instant()));
        return ResponseEntity.status(HttpStatus.CREATED).body(department);
    }

    @PatchMapping("/departments/{id}")
    public ResponseEntity<Department> renameDepartment(@PathVariable UUID id, @Valid @RequestBody NameRequest request) {
        adminScope.requireHospitalWide(adminScope.require());
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown department: " + id));
        department.setName(request.name());
        return ResponseEntity.ok(departmentRepository.save(department));
    }

    @DeleteMapping("/departments/{id}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable UUID id) {
        adminScope.requireHospitalWide(adminScope.require());
        departmentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- Specialty: department-scoped ---

    @PostMapping("/specialties")
    public ResponseEntity<Specialty> createSpecialty(@Valid @RequestBody CreateSpecialtyRequest request) {
        RlsContext caller = adminScope.require();
        adminScope.requireDepartmentAccess(caller, request.departmentId());
        Specialty specialty = specialtyRepository.save(
                new Specialty(UUID.randomUUID(), request.departmentId(), request.name(), clock.instant()));
        return ResponseEntity.status(HttpStatus.CREATED).body(specialty);
    }

    @PatchMapping("/specialties/{id}")
    public ResponseEntity<Specialty> renameSpecialty(@PathVariable UUID id, @Valid @RequestBody NameRequest request) {
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown specialty: " + id));
        adminScope.requireDepartmentAccess(adminScope.require(), specialty.getDepartmentId());
        specialty.setName(request.name());
        return ResponseEntity.ok(specialtyRepository.save(specialty));
    }

    @DeleteMapping("/specialties/{id}")
    public ResponseEntity<Void> deleteSpecialty(@PathVariable UUID id) {
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown specialty: " + id));
        adminScope.requireDepartmentAccess(adminScope.require(), specialty.getDepartmentId());
        specialtyRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- Appointment type: scoped via its specialty's department ---

    @PostMapping("/appointment-types")
    public ResponseEntity<AppointmentType> createAppointmentType(@Valid @RequestBody CreateAppointmentTypeRequest request) {
        Specialty specialty = specialtyRepository.findById(request.specialtyId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown specialty: " + request.specialtyId()));
        adminScope.requireDepartmentAccess(adminScope.require(), specialty.getDepartmentId());
        AppointmentType type = appointmentTypeRepository.save(new AppointmentType(
                UUID.randomUUID(), request.specialtyId(), request.name(),
                request.durationMinutes(), request.bufferMinutes(), clock.instant()));
        return ResponseEntity.status(HttpStatus.CREATED).body(type);
    }

    @PatchMapping("/appointment-types/{id}")
    public ResponseEntity<AppointmentType> updateAppointmentType(@PathVariable UUID id,
                                                                   @Valid @RequestBody CreateAppointmentTypeRequest request) {
        AppointmentType type = appointmentTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown appointment type: " + id));
        Specialty specialty = specialtyRepository.findById(type.getSpecialtyId())
                .orElseThrow(() -> new IllegalStateException("Specialty disappeared: " + type.getSpecialtyId()));
        adminScope.requireDepartmentAccess(adminScope.require(), specialty.getDepartmentId());
        type.setName(request.name());
        type.setDurationMinutes(request.durationMinutes());
        type.setBufferMinutes(request.bufferMinutes());
        return ResponseEntity.ok(appointmentTypeRepository.save(type));
    }

    @DeleteMapping("/appointment-types/{id}")
    public ResponseEntity<Void> deleteAppointmentType(@PathVariable UUID id) {
        AppointmentType type = appointmentTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown appointment type: " + id));
        Specialty specialty = specialtyRepository.findById(type.getSpecialtyId())
                .orElseThrow(() -> new IllegalStateException("Specialty disappeared: " + type.getSpecialtyId()));
        adminScope.requireDepartmentAccess(adminScope.require(), specialty.getDepartmentId());
        appointmentTypeRepository.deleteById(id);
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
        return ResponseEntity.status(HttpStatus.CREATED).body(holiday);
    }

    @DeleteMapping("/holidays/{id}")
    public ResponseEntity<Void> deleteHoliday(@PathVariable UUID id) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown holiday: " + id));
        RlsContext caller = adminScope.require();
        if (holiday.getDepartmentId() == null) {
            adminScope.requireHospitalWide(caller);
        } else {
            adminScope.requireDepartmentAccess(caller, holiday.getDepartmentId());
        }
        holidayRepository.deleteById(id);
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
