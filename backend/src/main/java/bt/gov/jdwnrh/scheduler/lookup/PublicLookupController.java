package bt.gov.jdwnrh.scheduler.lookup;

import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bt.gov.jdwnrh.scheduler.config.InMemoryRateLimiter;

/**
 * Public (no auth — see SecurityConfig's /api/lookup/** permitAll), see
 * design doc "Scope Expansion" -> E2. Reference number alone is not
 * treated as a secret; last name is the second identifier required to
 * resolve it (see V20's SECURITY DEFINER function), and this endpoint is
 * rate-limited per client IP on top of that — reference+lastname together
 * make blind enumeration impractical, but "impractical" still deserves a
 * rate limit, not just reliance on entropy.
 */
@RestController
@RequestMapping("/api/lookup")
public class PublicLookupController {

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final AppointmentLookupRepository repository;
    private final InMemoryRateLimiter rateLimiter;

    public PublicLookupController(AppointmentLookupRepository repository, InMemoryRateLimiter rateLimiter) {
        this.repository = repository;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/appointments")
    public ResponseEntity<LookupResponse> lookup(@RequestParam String referenceNumber, @RequestParam String lastName,
                                                   HttpServletRequest request) {
        String clientKey = "lookup:" + request.getRemoteAddr();
        if (!rateLimiter.tryAcquire(clientKey, MAX_ATTEMPTS, WINDOW)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        return repository.lookup(referenceNumber.trim(), lastName.trim())
                .map(r -> ResponseEntity.ok(LookupResponse.from(r)))
                // Deliberately the same 404 whether the reference doesn't exist or the
                // last name just didn't match — distinguishing the two would let an
                // attacker confirm a reference number is real without the second factor.
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    public record LookupResponse(String referenceNumber, String status, Instant startTime, String doctorName) {
        static LookupResponse from(AppointmentLookupRepository.LookupResult r) {
            return new LookupResponse(r.referenceNumber(), r.status(), r.startTime(),
                    "Dr. " + r.doctorFirstName() + " " + r.doctorLastName());
        }
    }
}
