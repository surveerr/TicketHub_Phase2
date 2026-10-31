package com.tickethub.notification.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Prints how the service is configured at startup, so a wrong setting is visible immediately. */
@Configuration
public class StartupCheck {

    private static final Logger log = LoggerFactory.getLogger(StartupCheck.class);

    @Bean
    ApplicationRunner notificationStartupReport(
            @Value("${notification.delivery-mode}") String mode,
            @Value("${notification.from:}") String from,
            @Value("${spring.kafka.bootstrap-servers}") String kafka,
            @Value("${kafka.topic.booking-confirmed}") String confirmed,
            @Value("${kafka.topic.payment-failed}") String failed,
            @Value("${kafka.topic.booking-cancelled}") String cancelled) {
        return args -> {
            log.info("Notification Service ready | mode={} | kafka={} | topics: {}, {}, {}",
                    mode, kafka, confirmed, failed, cancelled);
            if ("SMTP".equalsIgnoreCase(mode)) {
                if (from.isBlank() || from.startsWith("YOUR_")) {
                    log.error("\n\n*** E-MAIL NOT CONFIGURED ***\n"
                            + "Open src/main/resources/application-local.yml and put the TicketHub sender Gmail\n"
                            + "address and its 16-character Google App Password (or set env MAIL_USERNAME / MAIL_PASSWORD).\n"
                            + "Until then every e-mail will be marked FAILED and retried.\n");
                } else {
                    log.info("E-mail will be sent from {} through Gmail SMTP", from);
                }
            } else {
                log.warn("LOG mode: e-mails are only printed in this console, nothing is sent to Gmail.");
            }
        };
    }
}
