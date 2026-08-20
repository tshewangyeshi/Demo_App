package bt.gov.jdwnrh.scheduler.notification;

/**
 * The one channel this design implements for v1 (see design doc,
 * "Notifications" — SMS/push are documented future extension points behind
 * this same seam, never touching booking logic). Two implementations:
 * LoggingEmailSender (default) and SmtpEmailSender (app.mail.enabled=true).
 */
public interface EmailSender {
    void send(String recipientEmail, String subject, String body);
}
