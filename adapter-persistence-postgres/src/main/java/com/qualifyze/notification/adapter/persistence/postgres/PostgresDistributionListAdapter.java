package com.qualifyze.notification.adapter.persistence.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualifyze.notification.adapter.persistence.postgres.jpa.entity.DistributionListEntity;
import com.qualifyze.notification.adapter.persistence.postgres.jpa.repository.DistributionListJpaRepository;
import com.qualifyze.notification.application.port.out.audit.AuditEvent;
import com.qualifyze.notification.application.port.out.audit.AuditOutboxWriterPort;
import com.qualifyze.notification.application.port.out.persistence.DistributionListPort;
import com.qualifyze.notification.domain.model.DistributionList;
import com.qualifyze.notification.domain.model.DistributionListTarget;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PostgresDistributionListAdapter implements DistributionListPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DistributionListJpaRepository distributionListJpaRepository;
    private final AuditOutboxWriterPort auditOutboxWriterPort;

    public PostgresDistributionListAdapter(
            DistributionListJpaRepository distributionListJpaRepository,
            AuditOutboxWriterPort auditOutboxWriterPort
    ) {
        this.distributionListJpaRepository = distributionListJpaRepository;
        this.auditOutboxWriterPort = auditOutboxWriterPort;
    }

    @Override
    public List<DistributionListTarget> findTargets(String customerReference) {
        return distributionListJpaRepository
                .findByCustomerReferenceOrderById(customerReference.strip())
                .stream()
                .map(PostgresDistributionListAdapter::toTarget)
                .toList();
    }

    @Override
    @Transactional
    public void replaceForCustomer(DistributionList distributionList, String changedBy) {
        String customerReference = distributionList.customerReference();
        String actor = canonicalActor(changedBy);
        Set<DistributionListTarget> requestedTargets = Set.copyOf(distributionList.targets());

        Map<DistributionListTarget, DistributionListEntity> existingByTarget = distributionListJpaRepository
                .findByCustomerReferenceOrderById(customerReference)
                .stream()
                .collect(Collectors.toMap(PostgresDistributionListAdapter::toTarget, Function.identity()));

        // Only the diff is written: unchanged targets keep their row (and created_at),
        // and added/removed targets never overlap, so the unique constraint cannot be hit.
        List<DistributionListEntity> removed = existingByTarget.entrySet().stream()
                .filter(entry -> !requestedTargets.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        Set<DistributionListTarget> added = requestedTargets.stream()
                .filter(target -> !existingByTarget.containsKey(target))
                .collect(Collectors.toUnmodifiableSet());

        distributionListJpaRepository.deleteAll(removed);
        distributionListJpaRepository.saveAll(added.stream()
                .map(target -> new DistributionListEntity(customerReference, target.channel(), target.destination()))
                .toList());

        publishAuditEvents("notification.distribution-list.target-removed", customerReference, actor,
                removed.stream().map(PostgresDistributionListAdapter::toTarget).collect(Collectors.toUnmodifiableSet()));
        publishAuditEvents("notification.distribution-list.target-added", customerReference, actor, added);
    }

    private void publishAuditEvents(
            String eventType,
            String customerReference,
            String actor,
            Set<DistributionListTarget> targets
    ) {
        for (DistributionListTarget target : targets) {
            auditOutboxWriterPort.publish(new AuditEvent(
                    UUID.randomUUID().toString(),
                    eventType,
                    "DistributionList",
                    customerReference,
                    actor,
                    OffsetDateTime.now(),
                    stateAsString(target)
            ));
        }
    }

    private static DistributionListTarget toTarget(DistributionListEntity entity) {
        return new DistributionListTarget(entity.getDeliveryChannel(), entity.getNotificationTarget());
    }

    private static String canonicalActor(String changedBy) {
        if (changedBy == null || changedBy.isBlank()) {
            return "notification-service";
        }
        return changedBy.strip();
    }

    private String stateAsString(DistributionListTarget target) {
        try {
            return OBJECT_MAPPER.writeValueAsString(
                    new DistributionTargetState(target.channel().name(), target.destination()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize distribution target state", exception);
        }
    }

    private record DistributionTargetState(String deliveryChannel, String notificationTarget) {
    }
}
