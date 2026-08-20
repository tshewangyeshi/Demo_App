package bt.gov.jdwnrh.scheduler.department;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public reference data — no auth required (see SecurityConfig). This is the patient's first screen: browse department -> specialty -> appointment type. */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;
    private final SpecialtyRepository specialtyRepository;
    private final AppointmentTypeRepository appointmentTypeRepository;

    public DepartmentController(DepartmentRepository departmentRepository, SpecialtyRepository specialtyRepository,
                                 AppointmentTypeRepository appointmentTypeRepository) {
        this.departmentRepository = departmentRepository;
        this.specialtyRepository = specialtyRepository;
        this.appointmentTypeRepository = appointmentTypeRepository;
    }

    @GetMapping
    public List<DepartmentResponse> listDepartments() {
        return departmentRepository.findAll().stream()
                .map(d -> new DepartmentResponse(d.getId(), d.getName()))
                .toList();
    }

    @GetMapping("/{departmentId}/specialties")
    public List<SpecialtyResponse> listSpecialties(@PathVariable UUID departmentId) {
        return specialtyRepository.findByDepartmentId(departmentId).stream()
                .map(s -> new SpecialtyResponse(s.getId(), s.getDepartmentId(), s.getName()))
                .toList();
    }

    @GetMapping("/appointment-types")
    public List<AppointmentTypeResponse> listAppointmentTypes(@RequestParam UUID specialtyId) {
        return appointmentTypeRepository.findBySpecialtyId(specialtyId).stream()
                .map(t -> new AppointmentTypeResponse(t.getId(), t.getSpecialtyId(), t.getName(),
                        (int) t.duration().toMinutes(), (int) t.buffer().toMinutes()))
                .toList();
    }

    public record DepartmentResponse(UUID id, String name) {
    }

    public record SpecialtyResponse(UUID id, UUID departmentId, String name) {
    }

    public record AppointmentTypeResponse(UUID id, UUID specialtyId, String name, int durationMinutes, int bufferMinutes) {
    }
}
