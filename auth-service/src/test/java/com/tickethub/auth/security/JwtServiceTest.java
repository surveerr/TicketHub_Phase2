package com.tickethub.auth.security;

import com.tickethub.auth.config.AuthProperties;
import com.tickethub.auth.entity.Permission;
import com.tickethub.auth.entity.Role;
import com.tickethub.auth.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {
    private static final String SECRET = "unit-test-secret-key-that-is-at-least-32-bytes-long";

    private static JwtService service(String secret) {
        AuthProperties props = new AuthProperties();
        props.getJwt().setSecret(secret);
        return new JwtService(props);
    }

    private static User user() {
        Permission p = new Permission();
        p.setName("VIEW_EVENT");
        Role r = new Role();
        r.setName("USER");
        r.getPermissions().add(p);
        User u = new User();
        u.setId(7L);
        u.setEmail("a@b.com");
        u.getRoles().add(r);
        return u;
    }

    @Test
    void tokenRoundTripKeepsIdentityRolesAndPermissions() {
        JwtService jwt = service(SECRET);
        AuthenticatedUser au = jwt.parse(jwt.generateAccessToken(user())).orElseThrow();
        assertEquals(7L, au.id());
        assertEquals("a@b.com", au.email());
        assertTrue(au.roles().contains("USER"));
        assertTrue(au.permissions().contains("VIEW_EVENT"));
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = service(SECRET);
        assertTrue(jwt.parse(jwt.generateAccessToken(user()) + "x").isEmpty());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = service(SECRET).generateAccessToken(user());
        assertTrue(service("another-secret-key-that-is-also-at-least-32-bytes").parse(token).isEmpty());
    }
}
