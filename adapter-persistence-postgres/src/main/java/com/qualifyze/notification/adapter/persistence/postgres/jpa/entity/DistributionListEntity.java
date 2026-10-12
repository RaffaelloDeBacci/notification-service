package com.qualifyze.notification.adapter.persistence.postgres.jpa.entity;

import com.qualifyze.notification.domain.model.DistributionChannel;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "distribution_list")
public class DistributionListEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_reference", nullable = false)
    private String customerReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_channel", nullable = false)
    private DistributionChannel deliveryChannel;

    @Column(name = "notification_target", nullable = false)
    private String notificationTarget;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DistributionListEntity() {
    }

    public DistributionListEntity(String customerReference, DistributionChannel deliveryChannel, String notificationTarget) {
        this.customerReference = customerReference;
        this.deliveryChannel = deliveryChannel;
        this.notificationTarget = notificationTarget;
    }

    public DistributionChannel getDeliveryChannel() {
        return deliveryChannel;
    }

    public String getNotificationTarget() {
        return notificationTarget;
    }
}
