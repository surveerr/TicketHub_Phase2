package com.tickethub.auth.service;

import com.tickethub.auth.entity.AuditAction;
import com.tickethub.auth.entity.AuditLog;
import com.tickethub.auth.repository.AuditLogRepository;
import com.tickethub.auth.util.ClientInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Security audit trail. Runs in its own transaction so entries survive when the business transaction rolls back. */
@Service
public class AuditService {
    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, Long userId, ClientInfo client, String details) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setUserId(userId);
        if (client != null) {
            log.setIpAddress(client.ip());
            log.setUserAgent(client.userAgent());
        }
        if (details != null && details.length() > 500) details = details.substring(0, 500);
        log.setDetails(details);
        log.setCreatedAt(Instant.now());
        repository.save(log);
    }
}
