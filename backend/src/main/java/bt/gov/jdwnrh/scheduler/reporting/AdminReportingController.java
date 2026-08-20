package bt.gov.jdwnrh.scheduler.reporting;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operational reporting (appointment volume, doctor load, cancellation/
 * no-show rate) — see MVP Scope, "Admin can view operational reporting ...
 * from real seeded data, not hardcoded numbers". Under /api/admin/** so
 * SecurityConfig's existing DEPARTMENT_ADMIN/HOSPITAL_ADMIN/SUPER_ADMIN gate
 * applies; RLS on `appointment` itself is what actually scopes a department
 * admin to their own department's numbers (see ReportingRepository).
 */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportingController {

    private final ReportingService reportingService;

    public AdminReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/summary")
    public ReportingService.ReportSummary summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' must not be before 'from'");
        }
        return reportingService.summarize(from, to);
    }
}
