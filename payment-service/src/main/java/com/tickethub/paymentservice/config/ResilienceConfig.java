package com.tickethub.paymentservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Circuit breaker settings live in application.yml (resilience4j.circuitbreaker.instances.paymentProvider).
 * This class just logs state transitions so you can see the breaker open/close.
 */
@Configuration
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    @Bean
    public ApplicationRunner circuitBreakerLogger(CircuitBreakerRegistry registry) {
        return args -> registry.circuitBreaker("paymentProvider")
                .getEventPublisher()
                .onStateTransition(e -> log.warn("Circuit breaker '{}' transition: {}",
                        e.getCircuitBreakerName(), e.getStateTransition()));
    }
}
