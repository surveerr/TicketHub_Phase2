package com.tickethub.auth.config;

import com.tickethub.auth.security.JsonErrorWriter;
import com.tickethub.auth.security.JwtAuthenticationFilter;
import com.tickethub.auth.security.JwtService;
import com.tickethub.auth.security.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder(AuthProperties props) {
        return new BCryptPasswordEncoder(props.getSecurity().getBcryptStrength());
    }

    // The two filters are created here (not as @Component beans) so the servlet container does not register them twice.
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, AuthProperties props) throws Exception {
        http.csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> JsonErrorWriter.write(res, 401, "UNAUTHORIZED",
                        "Authentication is required or the access token is invalid/expired", req.getRequestURI()))
                .accessDeniedHandler((req, res, ex) -> JsonErrorWriter.write(res, 403, "FORBIDDEN",
                        "You do not have permission to perform this action", req.getRequestURI())))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                        "/actuator/**", "/error").permitAll()
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
                        "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
                        "/api/v1/auth/verify-email", "/api/v1/auth/resend-verification").permitAll()
                .anyRequest().authenticated())     // /users/**, /admin/** (+ @PreAuthorize), change-password, logout-all
            .addFilterBefore(new RateLimitFilter(props.getRateLimit()), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
