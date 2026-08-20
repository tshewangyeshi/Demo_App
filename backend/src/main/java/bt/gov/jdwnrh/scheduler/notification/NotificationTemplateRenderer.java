package bt.gov.jdwnrh.scheduler.notification;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * One template per NotificationEventType, keyed off the same payload map
 * NotificationEnqueuer's callers already write (see BookingService,
 * AppointmentLifecycleService, ReminderJob). Renders times in Asia/Thimphu —
 * per the design doc's timezone decision, timestamps are stored UTC but
 * NEVER shown to a human as UTC.
 */
@Component
public class NotificationTemplateRenderer {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy 'at' h:mm a").withZone(ZoneId.of("Asia/Thimphu"));

    public RenderedEmail render(NotificationEventType type, Map<String, Object> payload) {
        String ref = str(payload, "referenceNumber");
        return switch (type) {
            case BOOKING_CONFIRMED -> new RenderedEmail(
                    "Your JDWNRH appointment is confirmed (Ref: " + ref + ")",
                    "Your appointment is confirmed for " + time(payload, "startTime") + " (Bhutan time).\n"
                            + "Reference number: " + ref + "\n\n"
                            + "Please arrive 15 minutes early. Bring your CID and any referral documents.");
            case CANCELLED -> new RenderedEmail(
                    "Your JDWNRH appointment was cancelled (Ref: " + ref + ")",
                    "Your appointment (Reference: " + ref + ") has been cancelled. "
                            + "If this wasn't you, or you'd like to book a new time, please visit the scheduler again.");
            case RESCHEDULED -> new RenderedEmail(
                    "Your JDWNRH appointment was rescheduled (Ref: " + ref + ")",
                    "Your appointment (Reference: " + ref + ") has been rescheduled to "
                            + time(payload, "newStartTime") + " (Bhutan time).");
            case REMINDER_24H -> new RenderedEmail(
                    "Reminder: your JDWNRH appointment is tomorrow (Ref: " + ref + ")",
                    "This is a reminder that your appointment is scheduled for " + time(payload, "startTime")
                            + " (Bhutan time) — about 24 hours from now.\nReference number: " + ref);
            case REMINDER_2H -> new RenderedEmail(
                    "Reminder: your JDWNRH appointment is in 2 hours (Ref: " + ref + ")",
                    "This is a reminder that your appointment is scheduled for " + time(payload, "startTime")
                            + " (Bhutan time) — about 2 hours from now.\nReference number: " + ref);
        };
    }

    private static String str(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : "";
    }

    private static String time(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return "";
        }
        return DISPLAY_FORMAT.format(Instant.parse(value.toString()));
    }

    public record RenderedEmail(String subject, String body) {
    }
}
