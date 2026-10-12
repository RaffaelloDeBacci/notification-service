package com.qualifyze.notification.application.port.in.distribution;

import com.qualifyze.notification.domain.model.DistributionListTarget;

import java.util.Set;

public record UpsertDistributionListResult(
        String customerReference,
        Set<DistributionListTarget> targets
) {
}
