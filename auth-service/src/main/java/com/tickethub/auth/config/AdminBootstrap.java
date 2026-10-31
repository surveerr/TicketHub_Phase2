package com.tickethub.auth.config;

import com.tickethub.auth.entity.Role;
import com.tickethub.auth.entity.User;
import com.tickethub.auth.entity.UserStatus;
import com.tickethub.auth.repository.RoleRepository;
import com.tickethub.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;

/** Creates the first ADMIN account on startup if it does not exist yet (there is no other way to get one). */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AuthProperties props;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(AuthProperties props, UserRepository userRepository,
                          RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.props = props;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin cfg = props.getBootstrapAdmin();
        if (!cfg.isEnabled()) return;
        String email = cfg.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) return;

        Role admin = roleRepository.findByName("ADMIN").orElseThrow(() -> new IllegalStateException("ADMIN role missing - did the Flyway seed run?"));
        Role user = roleRepository.findByName("USER").orElseThrow(() -> new IllegalStateException("USER role missing"));

        User u = new User();
        u.setFirstName(cfg.getFirstName());
        u.setLastName(cfg.getLastName());
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(cfg.getPassword()));
        u.setStatus(UserStatus.ACTIVE);
        u.setEmailVerified(true);
        u.setPasswordChangedAt(Instant.now());
        u.getRoles().add(admin);
        u.getRoles().add(user);
        userRepository.save(u);
        log.warn("Bootstrap admin '{}' created. Change its password / set ADMIN_EMAIL + ADMIN_PASSWORD (or disable BOOTSTRAP_ADMIN_ENABLED) outside local development.", email);
    }
}
