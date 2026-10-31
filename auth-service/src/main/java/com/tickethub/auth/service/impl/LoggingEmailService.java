package com.tickethub.auth.service.impl;

import com.tickethub.auth.config.AuthProperties;
import com.tickethub.auth.entity.User;
import com.tickethub.auth.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Development implementation: "sends" the email by logging the link. Replace with a real sender before production
 * (and never log tokens there).
 */
@Service
public class LoggingEmailService implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);
    private final String baseUrl;

    public LoggingEmailService(AuthProperties props) {
        this.baseUrl = props.getFrontend().getBaseUrl();
    }

    @Override
    public void sendVerificationEmail(User user, String rawToken) {
        log.info("[EMAIL verify-email] to={} link={}/verify-email?token={}", user.getEmail(), baseUrl, rawToken);
    }

    @Override
    public void sendPasswordResetEmail(User user, String rawToken) {
        log.info("[EMAIL reset-password] to={} link={}/reset-password?token={}", user.getEmail(), baseUrl, rawToken);
    }
}
