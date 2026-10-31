package com.tickethub.auth.scheduler;

import com.tickethub.auth.repository.EmailVerificationTokenRepository;
import com.tickethub.auth.repository.PasswordResetTokenRepository;
import com.tickethub.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Deletes tokens that have expired, so the token tables do not grow forever. */
@Component
public class TokenCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(TokenCleanupScheduler.class);

    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final EmailVerificationTokenRepository verificationTokens;

    public TokenCleanupScheduler(RefreshTokenRepository refreshTokens, PasswordResetTokenRepository resetTokens,
                                 EmailVerificationTokenRepository verificationTokens) {
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.verificationTokens = verificationTokens;
    }

    @Scheduled(fixedDelayString = "${auth.cleanup-interval-ms:3600000}", initialDelayString = "${auth.cleanup-interval-ms:3600000}")
    @Transactional
    public void purgeExpired() {
        Instant now = Instant.now();
        int a = refreshTokens.deleteExpired(now);
        int b = resetTokens.deleteExpired(now);
        int c = verificationTokens.deleteExpired(now);
        if (a + b + c > 0) {
            log.info("Purged expired tokens: refresh={}, passwordReset={}, emailVerification={}", a, b, c);
        }
    }
}
