package com.qualifyze.notification.adapter.persistence.postgres.jpa.repository;

import com.qualifyze.notification.adapter.persistence.postgres.jpa.entity.DispatchLedgerEntity;
import com.qualifyze.notification.application.model.DispatchOutcome;
import com.qualifyze.notification.domain.model.DistributionChannel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchLedgerJpaRepository extends JpaRepository<DispatchLedgerEntity, Long> {

    boolean existsByIdempotencyKeyAndCustomerReferenceAndDeliveryChannelAndNotificationTargetAndOutcome(
            String idempotencyKey,
            String customerReference,
            DistributionChannel deliveryChannel,
            String notificationTarget,
            DispatchOutcome outcome
    );
}
