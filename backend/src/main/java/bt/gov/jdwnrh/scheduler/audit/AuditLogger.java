package bt.gov.jdwnrh.scheduler.audit;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Writes an AuditLog row — call this in the SAME transaction as the mutation
 * it records (same reasoning as NotificationEnqueuer for the outbox: if the
 * mutation rolls back, the audit entry should never have existed either).
 * previousValue/newValue are arbitrary small records — pass null for
 * whichever side doesn't apply (a CREATE has no previous, a DELETE has no new).
 */
@Component
public class AuditLogger {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditLogger(AuditLogRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void log(UUID actorId, String action, String resourceType, UUID resourceId, Object previousValue, Object newValue) {
        repository.save(new AuditLog(UUID.randomUUID(), actorId, action, resourceType, resourceId,
                toJson(previousValue), toJson(newValue), clock.instant()));
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit log value", e);
        }
    }
}
