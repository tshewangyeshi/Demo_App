package bt.gov.jdwnrh.scheduler.scheduling;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bt.gov.jdwnrh.scheduler.department.AppointmentType;
import bt.gov.jdwnrh.scheduler.department.AppointmentTypeRepository;

/**
 * Public reference data — no auth required (see SecurityConfig). This IS
 * the backend-computed availability the design doc insists on: the
 * frontend never generates or trusts its own idea of what's free (see
 * "Data & Correctness Architecture").
 */
@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {

    private final SlotGenerationService slotGenerationService;
    private final DoctorRepository doctorRepository;
    private final AppointmentTypeRepository appointmentTypeRepository;

    public AvailabilityController(SlotGenerationService slotGenerationService, DoctorRepository doctorRepository,
                                   AppointmentTypeRepository appointmentTypeRepository) {
        this.slotGenerationService = slotGenerationService;
        this.doctorRepository = doctorRepository;
        this.appointmentTypeRepository = appointmentTypeRepository;
    }

    @GetMapping
    public List<SlotResponse> availability(
            @RequestParam UUID doctorId,
            @RequestParam UUID appointmentTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown doctor: " + doctorId));
        AppointmentType appointmentType = appointmentTypeRepository.findById(appointmentTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown appointment type: " + appointmentTypeId));

        return slotGenerationService.generateSlots(doctorId, doctor.getDepartmentId(), date, appointmentType).stream()
                .map(slot -> new SlotResponse(slot.start(), slot.end()))
                .toList();
    }

    public record SlotResponse(java.time.Instant start, java.time.Instant end) {
    }
}
