package com.tickethub.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class JwtValidationGlobalFilter implements GlobalFilter, Ordered {
    private final SecretKey key;
    private static final Set<String> PUBLIC_PREFIXES = Set.of("/api/v1/auth/");
    private static final Set<String> BROWSING_PREFIXES = Set.of(
            "/api/v1/events", "/api/v1/shows", "/api/v1/venues", "/api/v1/screens",
            "/api/v1/locations", "/api/v1/enums");

    public JwtValidationGlobalFilter(@Value("${security.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    @Override public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        String path=exchange.getRequest().getURI().getPath(); HttpMethod method=exchange.getRequest().getMethod();
        if (isPublic(path,method)) return chain.filter(exchange);
        String header=exchange.getRequest().getHeaders().getFirst("Authorization");
        if(header==null || !header.startsWith("Bearer ") || !isValid(header.substring(7))) return unauthorized(exchange);
        return chain.filter(exchange);
    }
    private boolean isPublic(String path,HttpMethod method){
        if(path.startsWith("/actuator/health")) return true;
        if(path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/webjars")) return true;
        if(PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) return true;
        // Razorpay calls the webhook server-to-server (no JWT; the payment service verifies Razorpay's signature),
        // and the Razorpay test checkout page is a static page that asks for the token itself.
        if(path.equals("/api/payments/webhook") || path.equals("/checkout-test.html")) return true;
        return method==HttpMethod.GET && BROWSING_PREFIXES.stream().anyMatch(path::startsWith);
    }
    private boolean isValid(String token){ try{Jwts.parser().verifyWith(key).build().parseSignedClaims(token); return true;}catch(Exception e){return false;} }
    private Mono<Void> unauthorized(ServerWebExchange exchange){
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED); exchange.getResponse().getHeaders().add("Content-Type","application/json");
        byte[] body=("{\"timestamp\":\""+java.time.Instant.now()+"\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Login required: send header Authorization: Bearer <accessToken> (get it from POST /api/v1/auth/login)\"}").getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
    @Override public int getOrder(){return -100;}
}
