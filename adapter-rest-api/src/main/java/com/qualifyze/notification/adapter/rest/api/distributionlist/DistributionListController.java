package com.qualifyze.notification.adapter.rest.api.distributionlist;

import com.qualifyze.notification.adapter.rest.api.contract.DistributionListApi;
import com.qualifyze.notification.adapter.rest.api.contract.model.DistributionListChannel;
import com.qualifyze.notification.adapter.rest.api.contract.model.DistributionListResponse;
import com.qualifyze.notification.adapter.rest.api.contract.model.DistributionListTargetResponse;
import com.qualifyze.notification.adapter.rest.api.contract.model.UpsertDistributionListRequest;
import com.qualifyze.notification.application.port.in.distribution.UpsertDistributionListResult;
import com.qualifyze.notification.application.port.in.distribution.UpsertDistributionListUseCase;
import com.qualifyze.notification.domain.model.DistributionListTarget;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;

@RestController
public class DistributionListController implements DistributionListApi {

    private final UpsertDistributionListUseCase upsertDistributionListUseCase;
    private final DistributionListRequestMapper distributionListRequestMapper;

    public DistributionListController(
            UpsertDistributionListUseCase upsertDistributionListUseCase,
            DistributionListRequestMapper distributionListRequestMapper
    ) {
        this.upsertDistributionListUseCase = upsertDistributionListUseCase;
        this.distributionListRequestMapper = distributionListRequestMapper;
    }

    @Override
    public ResponseEntity<DistributionListResponse> upsertDistributionList(
            String customerReference,
            UpsertDistributionListRequest request,
            String xActor
    ) {
        UpsertDistributionListResult result = upsertDistributionListUseCase.upsert(
                distributionListRequestMapper.toCommand(customerReference, request, xActor)
        );

        DistributionListResponse response = new DistributionListResponse()
                .customerReference(result.customerReference())
                .targets(
                        result.targets()
                                .stream()
                                .sorted(Comparator.comparing(DistributionListTarget::channel)
                                        .thenComparing(DistributionListTarget::destination))
                                .map(target -> new DistributionListTargetResponse()
                                        .deliveryChannel(DistributionListChannel.fromValue(target.channel().name()))
                                        .notificationTarget(target.destination()))
                                .toList()
                );

        return ResponseEntity.ok(response);
    }
}
