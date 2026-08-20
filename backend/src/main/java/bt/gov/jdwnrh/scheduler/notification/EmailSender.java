package bt.gov.jdwnrh.scheduler.notification;

/**
 * The one channel this design implements for v1 (see design doc,
 * "Notifications" — SMS/push are documented future extension points behind
 * this same seam, never touching booking logic). Two implementations:
 * LoggingEmailSender (default) and SmtpEmailSender (app.mail.enabled=true).
 */
public interface EmailSender {

    void send(String recipientEmail, String subject, String body, Attachment attachment);

    /** filename/content/mimeType — used for the E3 .ics calendar attachment; content is plain text (not base64), the mail layer handles encoding. */
    record Attachment(String filename, String content, String mimeType) {
    }
}
