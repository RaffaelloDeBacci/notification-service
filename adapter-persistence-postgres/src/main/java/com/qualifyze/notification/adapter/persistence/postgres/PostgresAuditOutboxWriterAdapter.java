package com.qualifyze.notification.adapter.persistence.postgres;

import com.qualifyze.notification.adapter.persistence.postgres.jpa.entity.AuditOutboxEntity;
import com.qualifyze.notification.adapter.persistence.postgres.jpa.repository.AuditOutboxJpaRepository;
import com.qualifyze.notification.application.port.out.audit.AuditEvent;
import com.qualifyze.notification.application.port.out.audit.AuditOutboxWriterPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostgresAuditOutboxWriterAdapter implements AuditOutboxWriterPort {

    private final AuditOutboxJpaRepository auditOutboxJpaRepository;

    public PostgresAuditOutboxWriterAdapter(AuditOutboxJpaRepository auditOutboxJpaRepository) {
        this.auditOutboxJpaRepository = auditOutboxJpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(AuditEvent event) {
        auditOutboxJpaRepository.save(AuditOutboxEntity.from(event));
    }
}
