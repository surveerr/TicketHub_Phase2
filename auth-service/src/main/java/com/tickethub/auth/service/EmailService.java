package com.tickethub.auth.service;

import com.tickethub.auth.entity.User;

/** Plug a real sender (SMTP, SES, SendGrid...) in here. The raw token is only ever passed to this interface. */
public interface EmailService {
    void sendVerificationEmail(User user, String rawToken);
    void sendPasswordResetEmail(User user, String rawToken);
}
