package bt.gov.jdwnrh.scheduler.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Real delivery — active only when app.mail.enabled=true (see application.yml).
 * JavaMailSender itself comes from Spring Boot's mail auto-configuration,
 * driven by the standard spring.mail.host/port/username/password properties,
 * so this class only has to plug in the outbox-specific "from" address.
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
    public void send(String recipientEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(body);
        javaMailSender.send(message);
    }
}
