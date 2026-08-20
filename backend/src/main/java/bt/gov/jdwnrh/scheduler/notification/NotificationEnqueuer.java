package bt.gov.jdwnrh.scheduler.notification;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Writes a NotificationOutbox row — call this in the SAME transaction as the appointment mutation it announces. See design doc, "Sending is async via an outbox". */
@Component
public class NotificationEnqueuer {

    private final NotificationOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationEnqueuer(NotificationOutboxRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void enqueue(NotificationEventType eventType, UUID appointmentId, String recipientEmail, Map<String, Object> payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize notification payload", e);
        }

        repository.save(new NotificationOutbox(
                UUID.randomUUID(), appointmentId, eventType, recipientEmail, payloadJson, clock.instant()));
    }
}
