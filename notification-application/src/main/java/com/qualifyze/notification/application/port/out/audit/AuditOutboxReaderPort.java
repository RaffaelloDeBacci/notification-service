package com.qualifyze.notification.application.port.out.audit;

import java.time.OffsetDateTime;
import java.util.List;

public interface AuditOutboxReaderPort {
    List<SequencedAuditEvent> findUnpublished(int maxBatchSize);

    void markPublished(long sequence);

    long countUnpublished();

    OffsetDateTime findOldestUnpublishedAt();

    int deletePublishedBefore(OffsetDateTime cutoff);
}
