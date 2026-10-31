package com.tickethub.notification.service;

import com.tickethub.notification.exception.EmailDeliveryException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** Real e-mail through the TicketHub sender mailbox (Gmail SMTP). */
@Service
@ConditionalOnProperty(name = "notification.delivery-mode", havingValue = "SMTP")
public class SmtpEmailDeliveryService implements EmailDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailDeliveryService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailDeliveryService(JavaMailSender mailSender, @Value("${notification.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, EmailContent content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, "TicketHub");
            helper.setTo(to);
            helper.setSubject(content.subject());
            helper.setText(content.text(), content.html());
            mailSender.send(message);
            log.info("SMTP e-mail sent to {} : {}", to, content.subject());
        } catch (Exception e) {
            throw new EmailDeliveryException("SMTP send failed: " + e.getMessage(), e);
        }
    }
}
