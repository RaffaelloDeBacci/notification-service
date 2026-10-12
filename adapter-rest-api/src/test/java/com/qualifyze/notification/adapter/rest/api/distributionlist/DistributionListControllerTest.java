package com.qualifyze.notification.adapter.rest.api.distributionlist;

import com.qualifyze.notification.adapter.rest.api.contract.model.DistributionListChannel;
import com.qualifyze.notification.adapter.rest.api.contract.model.DistributionListResponse;
import com.qualifyze.notification.adapter.rest.api.contract.model.DistributionListTargetResponse;
import com.qualifyze.notification.adapter.rest.api.contract.model.UpsertDistributionListRequest;
import com.qualifyze.notification.application.port.in.distribution.UpsertDistributionListCommand;
import com.qualifyze.notification.application.port.in.distribution.UpsertDistributionListResult;
import com.qualifyze.notification.application.port.in.distribution.UpsertDistributionListUseCase;
import com.qualifyze.notification.domain.model.DistributionChannel;
import com.qualifyze.notification.domain.model.DistributionListTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributionListControllerTest {

    @Mock
    private UpsertDistributionListUseCase upsertDistributionListUseCase;

    @Mock
    private DistributionListRequestMapper distributionListRequestMapper;

    @InjectMocks
    private DistributionListController controller;

    @Test
    void shouldReturnSortedTargetsWhenDistributionListIsUpserted() {
        // Arrange
        UpsertDistributionListRequest request = new UpsertDistributionListRequest();
        UpsertDistributionListCommand command = new UpsertDistributionListCommand(
                "customer-1",
                Set.of(new DistributionListTarget(DistributionChannel.EMAIL, "a@example.com")),
                "jane.doe@qualifyze.com"
        );
        UpsertDistributionListResult useCaseResult = new UpsertDistributionListResult(
                "customer-1",
                Set.of(
                        new DistributionListTarget(DistributionChannel.SLACK, "#zeta"),
                        new DistributionListTarget(DistributionChannel.EMAIL, "z@example.com"),
                        new DistributionListTarget(DistributionChannel.EMAIL, "a@example.com")
                )
        );
        when(distributionListRequestMapper.toCommand("customer-1", request, "jane.doe@qualifyze.com")).thenReturn(command);
        when(upsertDistributionListUseCase.upsert(command)).thenReturn(useCaseResult);

        // Act
        ResponseEntity<DistributionListResponse> response = controller.upsertDistributionList("customer-1", request, "jane.doe@qualifyze.com");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("customer-1", response.getBody().getCustomerReference());
        assertEquals(3, response.getBody().getTargets().size());

        DistributionListTargetResponse first = response.getBody().getTargets().get(0);
        DistributionListTargetResponse second = response.getBody().getTargets().get(1);
        DistributionListTargetResponse third = response.getBody().getTargets().get(2);
        assertEquals(DistributionListChannel.EMAIL, first.getDeliveryChannel());
        assertEquals("a@example.com", first.getNotificationTarget());
        assertEquals(DistributionListChannel.EMAIL, second.getDeliveryChannel());
        assertEquals("z@example.com", second.getNotificationTarget());
        assertEquals(DistributionListChannel.SLACK, third.getDeliveryChannel());
        assertEquals("#zeta", third.getNotificationTarget());
    }

    @Test
    void shouldReturnEmptyTargetsWhenUseCaseReturnsNoTargets() {
        // Arrange
        UpsertDistributionListRequest request = new UpsertDistributionListRequest();
        UpsertDistributionListCommand command = new UpsertDistributionListCommand("customer-1", Set.of(), "jane.doe@qualifyze.com");
        when(distributionListRequestMapper.toCommand("customer-1", request, "jane.doe@qualifyze.com")).thenReturn(command);
        when(upsertDistributionListUseCase.upsert(command)).thenReturn(new UpsertDistributionListResult("customer-1", Set.of()));

        // Act
        ResponseEntity<DistributionListResponse> response = controller.upsertDistributionList("customer-1", request, "jane.doe@qualifyze.com");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().getTargets().size());
    }
}
