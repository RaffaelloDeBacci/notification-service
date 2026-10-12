package com.qualifyze.notification.adapter.rest.api.distributionlist;

import com.qualifyze.notification.adapter.rest.api.contract.model.DistributionListTargetRequest;
import com.qualifyze.notification.adapter.rest.api.contract.model.UpsertDistributionListRequest;
import com.qualifyze.notification.application.port.in.distribution.UpsertDistributionListCommand;
import com.qualifyze.notification.domain.model.DistributionChannel;
import com.qualifyze.notification.domain.model.DistributionListTarget;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DistributionListRequestMapper {

    public UpsertDistributionListCommand toCommand(String customerReference, UpsertDistributionListRequest request, String actor) {
        Set<DistributionListTarget> targets = request.getTargets()
                .stream()
                .map(DistributionListRequestMapper::toTarget)
                .collect(Collectors.toUnmodifiableSet());

        return new UpsertDistributionListCommand(customerReference, targets, actor);
    }

    private static DistributionListTarget toTarget(DistributionListTargetRequest requestTarget) {
        return new DistributionListTarget(
                DistributionChannel.from(requestTarget.getDeliveryChannel().toString()),
                requestTarget.getNotificationTarget()
        );
    }
}
