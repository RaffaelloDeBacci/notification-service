package com.qualifyze.notification.adapter.persistence.postgres.jpa.repository;

import com.qualifyze.notification.adapter.persistence.postgres.jpa.entity.DistributionListEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DistributionListJpaRepository extends JpaRepository<DistributionListEntity, Long> {
    List<DistributionListEntity> findByCustomerReferenceOrderById(String customerReference);
}
