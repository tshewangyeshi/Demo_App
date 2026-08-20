package bt.gov.jdwnrh.scheduler.scheduling;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public reference data — no auth required (see SecurityConfig). */
@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorProfileRepository doctorProfileRepository;

    public DoctorController(DoctorProfileRepository doctorProfileRepository) {
        this.doctorProfileRepository = doctorProfileRepository;
    }

    @GetMapping
    public List<DoctorResponse> listByDepartment(@RequestParam UUID departmentId) {
        return doctorProfileRepository.findByDepartment(departmentId).stream()
                .map(p -> new DoctorResponse(p.doctorId(), p.departmentId(), p.firstName(), p.lastName(), p.bio()))
                .toList();
    }

    public record DoctorResponse(UUID id, UUID departmentId, String firstName, String lastName, String bio) {
    }
}
