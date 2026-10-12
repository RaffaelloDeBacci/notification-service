package com.qualifyze.notification.application.usecase.distribution;

import com.qualifyze.notification.application.port.in.distribution.UpsertDistributionListCommand;
import com.qualifyze.notification.application.port.in.distribution.UpsertDistributionListResult;
import com.qualifyze.notification.application.port.in.distribution.UpsertDistributionListUseCase;
import com.qualifyze.notification.application.port.out.persistence.DistributionListPort;
import com.qualifyze.notification.domain.model.DistributionList;
import jakarta.inject.Named;

@Named
public class UpsertDistributionListService implements UpsertDistributionListUseCase {

    private final DistributionListPort distributionListPort;

    public UpsertDistributionListService(DistributionListPort distributionListPort) {
        this.distributionListPort = distributionListPort;
    }

    @Override
    public UpsertDistributionListResult upsert(UpsertDistributionListCommand command) {
        DistributionList distributionList = new DistributionList(command.customerReference(), command.targets());
        distributionListPort.replaceForCustomer(distributionList, command.changedBy());
        return new UpsertDistributionListResult(distributionList.customerReference(), distributionList.targets());
    }
}
