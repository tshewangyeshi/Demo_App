package bt.gov.jdwnrh.scheduler.appointment;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppointmentController {

    private final BookingService bookingService;
    private final AppointmentLifecycleService lifecycleService;

    public AppointmentController(BookingService bookingService, AppointmentLifecycleService lifecycleService) {
        this.bookingService = bookingService;
        this.lifecycleService = lifecycleService;
    }

    @PostMapping("/api/appointments")
    public ResponseEntity<AppointmentResponse> book(@Valid @RequestBody BookAppointmentRequest request) {
        Appointment appointment = bookingService.book(request.doctorId(), request.appointmentTypeId(), request.startTime());
        return ResponseEntity.status(HttpStatus.CREATED).body(AppointmentResponse.from(appointment));
    }

    @PatchMapping("/api/appointments/{id}/cancel")
    public ResponseEntity<AppointmentResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(AppointmentResponse.from(lifecycleService.cancel(id)));
    }

    @PatchMapping("/api/appointments/{id}/reschedule")
    public ResponseEntity<AppointmentResponse> reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest request) {
        return ResponseEntity.ok(AppointmentResponse.from(lifecycleService.reschedule(id, request.newStartTime())));
    }

    @ExceptionHandler(SlotUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleSlotUnavailable(SlotUnavailableException ex) {
        // See design doc, Interaction States: never a raw 409/500 for a lost
        // booking race — a clean message the patient can act on.
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
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
