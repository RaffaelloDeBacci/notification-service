package com.qualifyze.notification.application.port.in.distribution;

public interface UpsertDistributionListUseCase {
    UpsertDistributionListResult upsert(UpsertDistributionListCommand command);
}
