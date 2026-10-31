package com.tickethub.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Demo mode: prints the e-mail to the console instead of sending it. */
@Service
@ConditionalOnProperty(name = "notification.delivery-mode", havingValue = "LOG", matchIfMissing = true)
public class LogEmailDeliveryService implements EmailDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(LogEmailDeliveryService.class);

    @Override
    public void send(String to, EmailContent content) {
        log.info("\n========== [LOG MODE] E-MAIL ==========\nTo: {}\nSubject: {}\n\n{}\n=======================================",
                to, content.subject(), content.text());
    }
}
