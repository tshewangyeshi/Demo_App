package bt.gov.jdwnrh.scheduler.notification;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Written in the SAME transaction as the booking/cancellation/reschedule it
 * announces (see docs/designs/jdwnrh-scheduler.md, "Sending is async via an
 * outbox, not synchronous") — the booking commits regardless of whether the
 * email provider is up; a separate sender polls PENDING rows and delivers
 * them with retry.
 */
@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox {

    @Id
    private UUID id;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private NotificationEventType eventType;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationOutbox() {
        // JPA
    }

    public NotificationOutbox(UUID id, UUID appointmentId, NotificationEventType eventType,
                               String recipientEmail, String payloadJson) {
        this.id = id;
        this.appointmentId = appointmentId;
        this.eventType = eventType;
        this.recipientEmail = recipientEmail;
        this.payload = payloadJson;
    }

    public enum OutboxStatus {
        PENDING, SENT, FAILED
    }
}
