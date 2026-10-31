package com.tickethub.notification.service;

/** Strategy: SMTP (real e-mail) or LOG (demo / no credentials). */
public interface EmailDeliveryService {
    void send(String to, EmailContent content);
}
