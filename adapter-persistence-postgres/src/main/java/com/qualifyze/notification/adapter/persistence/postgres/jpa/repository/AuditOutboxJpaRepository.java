package com.qualifyze.notification.adapter.persistence.postgres.jpa.repository;

import com.qualifyze.notification.adapter.persistence.postgres.jpa.entity.AuditOutboxEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface AuditOutboxJpaRepository extends JpaRepository<AuditOutboxEntity, Long> {
    List<AuditOutboxEntity> findByPublishedAtIsNullOrderBySequenceAsc(Pageable pageable);

    AuditOutboxEntity findTopByPublishedAtIsNullOrderBySequenceAsc();

    long countByPublishedAtIsNull();

    int deleteByPublishedAtIsNotNullAndPublishedAtBefore(OffsetDateTime cutoff);
}
