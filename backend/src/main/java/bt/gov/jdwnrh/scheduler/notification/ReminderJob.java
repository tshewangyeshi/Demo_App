package bt.gov.jdwnrh.scheduler.notification;

import java.util.Map;

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
        for (var candidate : claimRepository.claim24hReminders(BATCH_SIZE)) {
            enqueue(NotificationEventType.REMINDER_24H, candidate);
        }
        for (var candidate : claimRepository.claim2hReminders(BATCH_SIZE)) {
            enqueue(NotificationEventType.REMINDER_2H, candidate);
        }
    }

    private void enqueue(NotificationEventType eventType, ReminderClaimRepository.ReminderCandidate candidate) {
        notificationEnqueuer.enqueue(eventType, candidate.appointmentId(), candidate.recipientEmail(), Map.of(
                "referenceNumber", candidate.referenceNumber(),
                "doctorId", candidate.doctorId().toString(),
                "startTime", candidate.startTime().toString()));
    }
}
