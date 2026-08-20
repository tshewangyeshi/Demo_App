package bt.gov.jdwnrh.scheduler.notification;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

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

    // .ics DTSTAMP/DTSTART/DTEND format — UTC, trailing 'Z'. Using UTC directly (not a
    // TZID block) is the simplest correct choice here: every calendar app converts a
    // 'Z'-suffixed timestamp to the VIEWER's own local timezone for display, which is
    // exactly "timezone-consistent with the UTC-storage decision" without needing to
    // embed Asia/Thimphu VTIMEZONE rules.
    private static final DateTimeFormatter ICS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"));

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

    /**
     * See design doc, "Scope Expansion" -> E3: .ics attachment on booking
     * confirmation, timezone-consistent with the UTC-storage decision.
     * Also attached to reschedule notices — same event, updated time — since
     * that's the same underlying need. Reference number carries forward
     * unchanged across a reschedule (see AppointmentLifecycleService), so
     * using it as the UID here means a reschedule's .ics naturally updates
     * the SAME calendar entry a calendar app already has, rather than
     * creating a duplicate — SEQUENCE distinguishes the two so clients know
     * the reschedule's version is newer.
     */
    public Optional<String> generateIcs(NotificationEventType type, Map<String, Object> payload) {
        String ref = str(payload, "referenceNumber");
        Instant start;
        Instant end;
        int sequence;
        switch (type) {
            case BOOKING_CONFIRMED -> {
                start = instant(payload, "startTime");
                end = instant(payload, "endTime");
                sequence = 0;
            }
            case RESCHEDULED -> {
                start = instant(payload, "newStartTime");
                end = instant(payload, "newEndTime");
                sequence = 1;
            }
            default -> {
                return Optional.empty();
            }
        }
        if (start == null || end == null || ref.isEmpty()) {
            return Optional.empty();
        }

        String ics = "BEGIN:VCALENDAR\r\n"
                + "VERSION:2.0\r\n"
                + "PRODID:-//JDWNRH Scheduler//EN\r\n"
                + "CALSCALE:GREGORIAN\r\n"
                + "METHOD:PUBLISH\r\n"
                + "BEGIN:VEVENT\r\n"
                + "UID:" + ref + "@jdwnrh.example\r\n"
                + "SEQUENCE:" + sequence + "\r\n"
                + "DTSTAMP:" + ICS_FORMAT.format(start) + "\r\n"
                + "DTSTART:" + ICS_FORMAT.format(start) + "\r\n"
                + "DTEND:" + ICS_FORMAT.format(end) + "\r\n"
                + "SUMMARY:JDWNRH Appointment (Ref: " + ref + ")\r\n"
                + "LOCATION:Jigme Dorji Wangchuck National Referral Hospital\r\n"
                + "DESCRIPTION:Appointment reference " + ref + ". Please arrive 15 minutes early.\r\n"
                + "STATUS:CONFIRMED\r\n"
                + "END:VEVENT\r\n"
                + "END:VCALENDAR\r\n";

        return Optional.of(ics);
    }

    private static Instant instant(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? Instant.parse(value.toString()) : null;
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
