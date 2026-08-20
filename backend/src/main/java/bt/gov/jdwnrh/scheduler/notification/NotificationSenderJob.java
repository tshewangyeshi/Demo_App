package bt.gov.jdwnrh.scheduler.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Polls PENDING notification_outbox rows and delivers them — see design
 * doc, "Sending is async via an outbox, not synchronous". A slow/down email
 * provider never adds latency to the booking response (that already
 * committed in its own transaction); a failed send here is a retryable row,
 * never a silently swallowed exception.
 */
@Component
public class NotificationSenderJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationSenderJob.class);
    private static final int BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 5;
    // 20s, 40s, 80s, 160s... — enough to ride out a brief provider blip without hammering it.
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(20);

    private final NotificationOutboxRepository repository;
    private final NotificationTemplateRenderer renderer;
    private final EmailSender emailSender;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationSenderJob(NotificationOutboxRepository repository, NotificationTemplateRenderer renderer,
                                  EmailSender emailSender, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.renderer = renderer;
        this.emailSender = emailSender;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.notifications.sender-interval-ms:15000}")
    @Transactional
    public void run() {
        List<UUID> ids = repository.claimPendingIds(BATCH_SIZE);
        if (ids.isEmpty()) {
            return;
        }
        for (NotificationOutbox outbox : repository.findAllById(ids)) {
            process(outbox);
        }
    }

    private void process(NotificationOutbox outbox) {
        try {
            Map<String, Object> payload = objectMapper.readValue(outbox.getPayload(), new TypeReference<Map<String, Object>>() {
            });
            NotificationTemplateRenderer.RenderedEmail rendered = renderer.render(outbox.getEventType(), payload);
            EmailSender.Attachment icsAttachment = renderer.generateIcs(outbox.getEventType(), payload)
                    .map(ics -> new EmailSender.Attachment("appointment.ics", ics, "text/calendar"))
                    .orElse(null);
            emailSender.send(outbox.getRecipientEmail(), rendered.subject(), rendered.body(), icsAttachment);
            outbox.markSent(clock.instant());
        } catch (Exception ex) {
            int attempts = outbox.getAttempts() + 1;
            if (attempts >= MAX_ATTEMPTS) {
                outbox.markPermanentlyFailed(attempts);
                log.error("Notification {} ({}) permanently failed after {} attempts", outbox.getId(), outbox.getEventType(), attempts, ex);
            } else {
                Instant nextAttempt = clock.instant().plus(BASE_BACKOFF.multipliedBy(1L << attempts)); // 20s * 2^attempts
                outbox.markFailedAndRetry(attempts, nextAttempt);
                log.warn("Notification {} ({}) failed (attempt {}/{}), retrying at {}: {}",
                        outbox.getId(), outbox.getEventType(), attempts, MAX_ATTEMPTS, nextAttempt, ex.getMessage());
            }
        }
        repository.save(outbox);
    }
}
