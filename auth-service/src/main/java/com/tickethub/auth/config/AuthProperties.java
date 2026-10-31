package com.tickethub.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under the "auth." prefix in application.yml. */
@Getter @Setter
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {
    private Jwt jwt = new Jwt();
    private Security security = new Security();
    private RateLimit rateLimit = new RateLimit();
    private Frontend frontend = new Frontend();
    private BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();

    @Getter @Setter
    public static class Jwt {
        private String secret;
        private String issuer = "tickethub-auth-service";
        private long accessTokenTtlMinutes = 15;
        private long refreshTokenTtlDays = 7;
    }

    @Getter @Setter
    public static class Security {
        private int maxFailedAttempts = 5;
        private long lockDurationMinutes = 15;
        private boolean requireEmailVerification = false;
        private long passwordResetTtlMinutes = 30;
        private long emailVerificationTtlHours = 24;
        private int bcryptStrength = 12;
    }

    @Getter @Setter
    public static class RateLimit {
        private boolean enabled = true;
        private int maxRequests = 10;
        private long windowSeconds = 60;
    }

    @Getter @Setter
    public static class Frontend {
        private String baseUrl = "http://localhost:3000";
    }

    @Getter @Setter
    public static class BootstrapAdmin {
        private boolean enabled = true;
        private String email = "admin@tickethub.local";
        private String password = "Admin@12345";
        private String firstName = "Admin";
        private String lastName = "TicketHub";
    }
}
