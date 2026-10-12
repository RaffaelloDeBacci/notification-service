package com.qualifyze.notification.application.port.out.persistence;

import com.qualifyze.notification.domain.model.DistributionList;
import com.qualifyze.notification.domain.model.DistributionListTarget;

import java.util.List;

/**
 * Reads and writes the distribution list aggregate.
 *
 * <p>Writes emit audit events and must therefore run inside a transaction.
 */
public interface DistributionListPort {
    void replaceForCustomer(DistributionList distributionList, String changedBy);

    List<DistributionListTarget> findTargets(String customerReference);
}
