package bt.gov.jdwnrh.scheduler.audit;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Every admin-console mutation (department/specialty/appointment-type/
 * holiday CRUD, staff account creation, doctor schedule/leave changes,
 * access-request review) writes one row here — see AuditLogger. No RLS
 * (see V3's closing comment: "backend-only, never queried with request-
 * scoped RLS context"); access is gated at the application layer instead
 * (AdminAuditLogController, HOSPITAL_ADMIN/SUPER_ADMIN only).
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false)
    private String action;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_value")
    private String previousValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value")
    private String newValue;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditLog() {
        // JPA
    }

    public AuditLog(UUID id, UUID actorId, String action, String resourceType, UUID resourceId,
                     String previousValueJson, String newValueJson, Instant now) {
        this.id = id;
        this.actorId = actorId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.previousValue = previousValueJson;
        this.newValue = newValueJson;
        this.occurredAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
