package com.qualifyze.notification.domain.model;

import java.util.Set;

public record DistributionList(
        String customerReference,
        Set<DistributionListTarget> targets
) {
    public DistributionList {
        customerReference = Require.text(customerReference, "customerReference");
        targets = Require.notEmpty(targets, "targets");
    }
}
