package bt.gov.jdwnrh.scheduler.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default EmailSender — this is a portfolio/demo build (see design doc,
 * Premises) with no real SMTP account provisioned, so "sending" means
 * logging the fully-rendered email visibly rather than either (a) requiring
 * real credentials just to run the app locally, or (b) silently no-op'ing
 * with nothing to show the send even happened. Swap to SmtpEmailSender by
 * setting app.mail.enabled=true plus spring.mail.* (see application.yml).
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String recipientEmail, String subject, String body, Attachment attachment) {
        if (attachment != null) {
            log.info("EMAIL (not actually sent — app.mail.enabled=false) to={} subject=\"{}\" attachment={} ({} bytes)\n{}",
                    recipientEmail, subject, attachment.filename(), attachment.content().length(), body);
        } else {
            log.info("EMAIL (not actually sent — app.mail.enabled=false) to={} subject=\"{}\"\n{}", recipientEmail, subject, body);
        }
    }
}
