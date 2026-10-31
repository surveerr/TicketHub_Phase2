package com.tickethub.paymentservice.client;

import com.tickethub.paymentservice.exception.BookingNotFoundException;
import com.tickethub.paymentservice.exception.BookingServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Real REST client for the Booking Service (active when BOOKING_CLIENT=REST).
 *
 * Booking is found by NAME in Eureka (booking.service.name, default "booking-service"), round-robin across its
 * instances; booking.service.base-url is only a fallback when Eureka knows no instance (ST-11).
 *
 * Contract with Booking (INTEGRATION-CONTRACT.md):
 *   GET  /api/v1/bookings/{id}                       -> { bookingId, customerId, amount, currency, status, expiryAt, ... }
 *   POST /internal/v1/bookings/{id}/payment-result   -> body BookingPaymentResult; 204 = accepted,
 *                                                       404/409/422 = refused (unknown / not payable / expired)
 *
 * Every call has explicit connect/read timeouts (ST-16). Reads are retried with backoff (ST-17); the result
 * notification is NOT retried inline - it is retried by the DB-backed sync job instead. Both go through a
 * circuit breaker (ST-18).
 */
@Component
@ConditionalOnProperty(prefix = "booking.client", name = "name", havingValue = "REST")
public class RestBookingClient implements BookingClient {

    private static final Logger log = LoggerFactory.getLogger(RestBookingClient.class);
    private static final String CB = "bookingService";

    private final DiscoveryClient discoveryClient;
    private final String serviceName;
    private final String fallbackBaseUrl;
    private final SimpleClientHttpRequestFactory requestFactory;
    private final AtomicInteger next = new AtomicInteger();
    private final SecretKey internalKey;

    public RestBookingClient(DiscoveryClient discoveryClient,
                             @Value("${booking.service.name:booking-service}") String serviceName,
                             @Value("${booking.service.base-url:http://localhost:8082}") String fallbackBaseUrl,
                             @Value("${booking.service.connect-timeout-ms:1000}") int connectTimeoutMs,
                             @Value("${booking.service.read-timeout-ms:3000}") int readTimeoutMs,
                             @Value("${security.internal-jwt-secret}") String internalSecret) {
        this.discoveryClient = discoveryClient;
        this.serviceName = serviceName;
        this.fallbackBaseUrl = fallbackBaseUrl;
        this.requestFactory = new SimpleClientHttpRequestFactory();
        this.requestFactory.setConnectTimeout(connectTimeoutMs);
        this.requestFactory.setReadTimeout(readTimeoutMs);
        this.internalKey = Keys.hmacShaKeyFor(internalSecret.getBytes(StandardCharsets.UTF_8));
    }

    /** One RestClient per call, pointed at the next Booking instance known to Eureka. */
    private RestClient client() {
        String baseUrl = fallbackBaseUrl;
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
            if (!instances.isEmpty()) {
                baseUrl = instances.get(Math.floorMod(next.getAndIncrement(), instances.size())).getUri().toString();
            } else {
                log.debug("Eureka knows no '{}' instance, using fallback {}", serviceName, fallbackBaseUrl);
            }
        } catch (RuntimeException e) {
            log.warn("Eureka lookup for '{}' failed ({}), using fallback {}", serviceName, e.toString(), fallbackBaseUrl);
        }
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    @Retry(name = "bookingRead")
    @CircuitBreaker(name = CB, fallbackMethod = "getBookingFallback")
    public BookingDetails getBooking(Long bookingId) {
        try {
            String corrId = org.slf4j.MDC.get("correlationId");
            var req = client().get()
                    .uri("/internal/v1/bookings/{id}", bookingId)
                    .header("Authorization", "Bearer " + internalToken());
            if (corrId != null && !corrId.isBlank()) {
                req.header("X-Correlation-Id", corrId);
            }
            BookingDetails booking = req.retrieve().body(BookingDetails.class);
            if (booking == null) {
                throw new BookingServiceUnavailableException("Booking Service returned an empty response");
            }
            return booking;
        } catch (HttpClientErrorException.NotFound e) {
            throw new BookingNotFoundException("Booking not found: " + bookingId);
        } catch (RestClientException e) {
            throw new BookingServiceUnavailableException("Booking Service call failed: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = CB, fallbackMethod = "notifyFallback")
    public BookingSyncResult notifyPaymentResult(Long bookingId, BookingPaymentResult result) {
        try {
            String corrId = org.slf4j.MDC.get("correlationId");
            var req = client().post()
                    .uri("/internal/v1/bookings/{id}/payment-result", bookingId)
                    .header("Authorization", "Bearer " + internalToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result);
            if (corrId != null && !corrId.isBlank()) {
                req.header("X-Correlation-Id", corrId);
            }
            req.retrieve().toBodilessEntity();
            return BookingSyncResult.DELIVERED;
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            if (status == 404 || status == 409 || status == 422) {
                log.warn("Booking {} refused payment result {} with HTTP {}: {}", bookingId,
                        result.paymentReference(), status, e.getResponseBodyAsString());
                return BookingSyncResult.REJECTED;
            }
            throw new BookingServiceUnavailableException("Booking Service answered HTTP " + status, e);
        } catch (RestClientException e) {
            throw new BookingServiceUnavailableException("Booking Service call failed: " + e.getMessage(), e);
        }
    }

    private String internalToken() { Date now=new Date(); return Jwts.builder().subject("payment-service").claim("role","INTERNAL_SERVICE").claim("roles",java.util.List.of("INTERNAL_SERVICE")).issuedAt(now).expiration(new Date(now.getTime()+300000)).signWith(internalKey).compact(); }

    // ---- fallbacks: original parameters + a trailing exception; the closest exception type wins ----

    /** A genuine "no such booking" must stay a 404, not become "service unavailable". */
    public BookingDetails getBookingFallback(Long bookingId, BookingNotFoundException e) {
        throw e;
    }

    public BookingDetails getBookingFallback(Long bookingId, Throwable t) {
        log.error("Could not read booking {}: {}", bookingId, t.toString());
        throw new BookingServiceUnavailableException("Booking service unavailable, please try again shortly", t);
    }

    public BookingSyncResult notifyFallback(Long bookingId, BookingPaymentResult result, Throwable t) {
        log.warn("Could not tell booking {} about payment {} ({}); will retry", bookingId,
                result.paymentReference(), t.toString());
        return BookingSyncResult.RETRY;
    }
}
