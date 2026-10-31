package com.tickethub.booking.client;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Component
public class EventServiceClient implements EventClient {
    private final RestClient.Builder builder;
    private final String baseUrl;
    private final String jwtSecret;
    private final SecretKey key;
    private final DiscoveryClient discoveryClient;
    private final boolean useEureka;

    public EventServiceClient(RestClient.Builder builder,
                              @Value("${services.event.base-url:http://localhost:8081}") String baseUrl,
                              @Value("${security.internal-jwt-secret:change-this-secret-key-for-local-v01-must-be-at-least-32-bytes}") String jwtSecret,
                              DiscoveryClient discoveryClient,
                              @Value("${services.event.use-eureka:false}") boolean useEureka) {
        this.builder=builder; this.baseUrl=baseUrl; this.jwtSecret=jwtSecret; this.key=Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)); this.discoveryClient=discoveryClient; this.useEureka=useEureka;
    }

    private RestClient client(){
        if (!useEureka) {
            return builder.baseUrl(baseUrl).build();
        }
        var instances=discoveryClient.getInstances("event-show-service");
        String resolved=instances.isEmpty()?baseUrl:instances.get(0).getUri().toString();
        return builder.baseUrl(resolved).build();
    }
    private String token(){
        Instant now=Instant.now();
        return Jwts.builder().subject("booking-service").claim("role","INTERNAL_SERVICE").issuedAt(java.util.Date.from(now)).expiration(java.util.Date.from(now.plusSeconds(300))).signWith(key).compact();
    }
    private RestClient.RequestHeadersSpec<?> auth(RestClient.RequestHeadersSpec<?> r){
        String corrId = org.slf4j.MDC.get("correlationId");
        if (corrId != null && !corrId.isBlank()) {
            r = r.header("X-Correlation-Id", corrId);
        }
        return r.header("Authorization","Bearer "+token());
    }

    @Override public ShowInfo getShow(Long showId){
        try {
            var r=auth(client().get().uri("/internal/v1/shows/{id}",showId)).retrieve().body(InternalShowResponse.class);
            return new ShowInfo(r.showId(), r.status(), r.eventStatus(), r.startTime(), r.endTime());
        } catch(RestClientException e){ throw new EventServiceException("Unable to validate show: "+e.getMessage(), e); }
    }
    @Override public List<SeatInfo> getShowSeats(Long showId){
        try {
            var r=auth(client().get().uri("/internal/v1/shows/{id}/seats",showId)).retrieve().body(new ParameterizedTypeReference<List<SeatDto>>(){});
            if(r==null) return List.of();
            return r.stream().map(x -> new SeatInfo(x.id(), x.status(), x.price())).toList();
        } catch(RestClientException e){ throw new EventServiceException("Unable to read show seats: "+e.getMessage(), e); }
    }
    @Override public void holdSeats(Long showId, Long bookingId, List<Long> seatIds, Instant expiresAt){
        action(showId,"hold",Map.of("bookingId",bookingId,"seatIds",seatIds,"holdExpiresAt",expiresAt));
    }
    @Override public void confirmSeats(Long showId, Long bookingId){ action(showId,"confirm",Map.of("bookingId",bookingId)); }
    @Override public void releaseSeats(Long showId, Long bookingId){ action(showId,"release",Map.of("bookingId",bookingId)); }
    private void action(Long showId, String op, Object body) {
        try {
            client().post()
                    .uri("/internal/v1/shows/{id}/seats/" + op, showId)
                    .header("Authorization", "Bearer " + token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new EventServiceException(
                    "Event seat " + op + " failed: " + e.getMessage(), e);
        }
    }
    public record InternalShowResponse(Long showId, String status, String eventStatus, Instant startTime, Instant endTime, List<Object> tickets) {}
    public record SeatDto(Long id, String status, BigDecimal price) {}
}
class EventServiceException extends RuntimeException { EventServiceException(String m,Throwable c){super(m,c);} }
