package bt.gov.jdwnrh.scheduler.notification;

import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Real delivery — active only when app.mail.enabled=true (see application.yml).
 * JavaMailSender itself comes from Spring Boot's mail auto-configuration,
 * driven by the standard spring.mail.host/port/username/password properties,
 * so this class only has to plug in the outbox-specific "from" address.
 * Uses MimeMessageHelper (multipart) rather than SimpleMailMessage because
 * SimpleMailMessage cannot carry an attachment — needed for the E3 .ics file.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;
    private final String fromAddress;

    public SmtpEmailSender(JavaMailSender javaMailSender, @Value("${app.mail.from}") String fromAddress) {
        this.javaMailSender = javaMailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String recipientEmail, String subject, String body, Attachment attachment) {
        MimeMessage message = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, attachment != null);
            helper.setFrom(fromAddress);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(body);
            if (attachment != null) {
                helper.addAttachment(attachment.filename(),
                        new jakarta.mail.util.ByteArrayDataSource(attachment.content().getBytes(StandardCharsets.UTF_8), attachment.mimeType()));
            }
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to build email message", e);
        }
        javaMailSender.send(message);
    }
}
