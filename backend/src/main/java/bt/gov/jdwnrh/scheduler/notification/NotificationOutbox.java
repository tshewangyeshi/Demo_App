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
 * email provider is up; NotificationSenderJob polls PENDING rows and
 * delivers them with retry/backoff (see markSent/markFailedAndRetry).
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

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationOutbox() {
        // JPA
    }

    public NotificationOutbox(UUID id, UUID appointmentId, NotificationEventType eventType,
                               String recipientEmail, String payloadJson, Instant now) {
        this.id = id;
        this.appointmentId = appointmentId;
        this.eventType = eventType;
        this.recipientEmail = recipientEmail;
        this.payload = payloadJson;
        this.createdAt = now;
        this.nextAttemptAt = now; // eligible for the very next sender poll
    }

    public void markSent(Instant now) {
        this.status = OutboxStatus.SENT;
        this.sentAt = now;
    }

    /** attempts is incremented BEFORE this is called (see NotificationSenderJob) — nextAttemptAt is the backoff delay already computed from that count. */
    public void markFailedAndRetry(int attempts, Instant nextAttemptAt) {
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markPermanentlyFailed(int attempts) {
        this.attempts = attempts;
        this.status = OutboxStatus.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public NotificationEventType getEventType() {
        return eventType;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public enum OutboxStatus {
        PENDING, SENT, FAILED
    }
}
