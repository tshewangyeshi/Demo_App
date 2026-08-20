package bt.gov.jdwnrh.scheduler.notification;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 24h and 2h pre-appointment reminders — see design doc, "Notifications".
 * Claiming is exclusive across concurrently-running backend instances
 * (see ReminderClaimRepository / V18); enqueuing reuses the exact same
 * outbox + NotificationSenderJob delivery path as booking/cancel/reschedule.
 */
@Component
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);
    private static final int BATCH_SIZE = 50;

    private final ReminderClaimRepository claimRepository;
    private final NotificationEnqueuer notificationEnqueuer;

    public ReminderJob(ReminderClaimRepository claimRepository, NotificationEnqueuer notificationEnqueuer) {
        this.claimRepository = claimRepository;
        this.notificationEnqueuer = notificationEnqueuer;
    }

    @Scheduled(fixedDelayString = "${app.notifications.reminder-interval-ms:60000}")
    @Transactional
    public void run() {
        List<ReminderClaimRepository.ReminderCandidate> claimed24h = claimRepository.claim24hReminders(BATCH_SIZE);
        for (var candidate : claimed24h) {
            enqueue(NotificationEventType.REMINDER_24H, candidate);
        }
        List<ReminderClaimRepository.ReminderCandidate> claimed2h = claimRepository.claim2hReminders(BATCH_SIZE);
        for (var candidate : claimed2h) {
            enqueue(NotificationEventType.REMINDER_2H, candidate);
        }
        if (!claimed24h.isEmpty() || !claimed2h.isEmpty()) {
            log.info("Reminder job claimed {} 24h and {} 2h reminder(s)", claimed24h.size(), claimed2h.size());
        }
    }

    private void enqueue(NotificationEventType eventType, ReminderClaimRepository.ReminderCandidate candidate) {
        notificationEnqueuer.enqueue(eventType, candidate.appointmentId(), candidate.recipientEmail(), Map.of(
                "referenceNumber", candidate.referenceNumber(),
                "doctorId", candidate.doctorId().toString(),
                "startTime", candidate.startTime().toString()));
    }
}
