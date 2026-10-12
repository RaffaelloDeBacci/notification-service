package com.qualifyze.notification.adapter.persistence.postgres;

import com.qualifyze.notification.adapter.persistence.postgres.jpa.entity.AuditOutboxEntity;
import com.qualifyze.notification.adapter.persistence.postgres.jpa.repository.AuditOutboxJpaRepository;
import com.qualifyze.notification.application.port.out.audit.AuditOutboxReaderPort;
import com.qualifyze.notification.application.port.out.audit.SequencedAuditEvent;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class PostgresAuditOutboxReaderAdapter implements AuditOutboxReaderPort {

    private final AuditOutboxJpaRepository auditOutboxJpaRepository;

    public PostgresAuditOutboxReaderAdapter(AuditOutboxJpaRepository auditOutboxJpaRepository) {
        this.auditOutboxJpaRepository = auditOutboxJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SequencedAuditEvent> findUnpublished(int maxBatchSize) {
        if (maxBatchSize <= 0) {
            return List.of();
        }
        return auditOutboxJpaRepository.findByPublishedAtIsNullOrderBySequenceAsc(PageRequest.of(0, maxBatchSize))
                .stream()
                .map(AuditOutboxEntity::toSequencedAuditEvent)
                .toList();
    }

    @Override
    @Transactional
    public void markPublished(long sequence) {
        AuditOutboxEntity entity = auditOutboxJpaRepository.findById(sequence)
                .orElseThrow(() -> new IllegalStateException("Audit outbox sequence not found: " + sequence));
        entity.markPublished(OffsetDateTime.now());
        auditOutboxJpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnpublished() {
        return auditOutboxJpaRepository.countByPublishedAtIsNull();
    }

    @Override
    @Transactional(readOnly = true)
    public OffsetDateTime findOldestUnpublishedAt() {
        AuditOutboxEntity entity = auditOutboxJpaRepository.findTopByPublishedAtIsNullOrderBySequenceAsc();
        return entity == null ? null : entity.getCreatedAt();
    }

    @Override
    @Transactional
    public int deletePublishedBefore(OffsetDateTime cutoff) {
        return auditOutboxJpaRepository.deleteByPublishedAtIsNotNullAndPublishedAtBefore(cutoff);
    }
}
