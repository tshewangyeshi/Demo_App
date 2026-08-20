package bt.gov.jdwnrh.scheduler.appointment;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Front-desk / department daily queue (nurse, receptionist, department
 * admin) — see SecurityConfig for the role gate. RLS
 * (appointment_department_scoped_staff) narrows every query here to the
 * caller's own department automatically; hospital/super admin see everything.
 */
@RestController
@RequestMapping("/api/staff")
public class StaffAppointmentController {

    private final AppointmentQueryService queryService;
    private final AppointmentLifecycleService lifecycleService;

    public StaffAppointmentController(AppointmentQueryService queryService, AppointmentLifecycleService lifecycleService) {
        this.queryService = queryService;
        this.lifecycleService = lifecycleService;
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> dailyQueue(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queryService.listForDay(date).stream().map(AppointmentResponse::from).toList();
    }

    @PatchMapping("/appointments/{id}/check-in")
    public ResponseEntity<AppointmentResponse> checkIn(@PathVariable UUID id) {
        return ResponseEntity.ok(AppointmentResponse.from(
                lifecycleService.transition(id, AppointmentStatus.CHECKED_IN, "Checked in at front desk")));
    }

    @PatchMapping("/appointments/{id}/waiting")
    public ResponseEntity<AppointmentResponse> moveToWaiting(@PathVariable UUID id) {
        return ResponseEntity.ok(AppointmentResponse.from(
                lifecycleService.transition(id, AppointmentStatus.WAITING, "Moved to waiting area")));
    }

    @PatchMapping("/appointments/{id}/no-show")
    public ResponseEntity<AppointmentResponse> noShow(@PathVariable UUID id) {
        return ResponseEntity.ok(AppointmentResponse.from(
                lifecycleService.transition(id, AppointmentStatus.NO_SHOW, "Marked no-show")));
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
}
