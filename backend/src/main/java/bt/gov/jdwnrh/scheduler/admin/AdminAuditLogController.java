package bt.gov.jdwnrh.scheduler.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bt.gov.jdwnrh.scheduler.audit.AuditLog;
import bt.gov.jdwnrh.scheduler.audit.AuditLogRepository;

/**
 * Read side of the audit trail — see AuditLogger for what writes it.
 * Hospital-wide governance concern, so unlike most of /api/admin/** this is
 * deliberately HOSPITAL_ADMIN/SUPER_ADMIN only, not department-scoped.
 */
@RestController
@RequestMapping("/api/admin/audit-log")
public class AdminAuditLogController {

    private final AdminScope adminScope;
    private final AuditLogRepository repository;

    public AdminAuditLogController(AdminScope adminScope, AuditLogRepository repository) {
        this.adminScope = adminScope;
        this.repository = repository;
    }

    @GetMapping
    public List<AuditLogResponse> list(@RequestParam(required = false) String resourceType) {
        adminScope.requireHospitalWide(adminScope.require());
        Sort mostRecentFirst = Sort.by(Sort.Direction.DESC, "occurredAt");
        List<AuditLog> entries = resourceType != null
                ? repository.findByResourceType(resourceType, mostRecentFirst)
                : repository.findAll(mostRecentFirst);
        return entries.stream().map(AuditLogResponse::from).toList();
    }

    public record AuditLogResponse(UUID id, UUID actorId, String action, String resourceType, UUID resourceId,
                                    String previousValue, String newValue, Instant occurredAt) {
        static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(log.getId(), log.getActorId(), log.getAction(), log.getResourceType(),
                    log.getResourceId(), log.getPreviousValue(), log.getNewValue(), log.getOccurredAt());
        }
    }
}
