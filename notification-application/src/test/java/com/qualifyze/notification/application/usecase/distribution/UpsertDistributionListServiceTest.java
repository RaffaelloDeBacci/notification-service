package com.qualifyze.notification.application.usecase.distribution;

import com.qualifyze.notification.application.port.in.distribution.UpsertDistributionListCommand;
import com.qualifyze.notification.application.port.in.distribution.UpsertDistributionListResult;
import com.qualifyze.notification.application.port.out.persistence.DistributionListPort;
import com.qualifyze.notification.domain.model.DistributionChannel;
import com.qualifyze.notification.domain.model.DistributionList;
import com.qualifyze.notification.domain.model.DistributionListTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UpsertDistributionListServiceTest {

    @Mock
    private DistributionListPort distributionListPort;

    @InjectMocks
    private UpsertDistributionListService service;

    @Captor
    private ArgumentCaptor<DistributionList> distributionListCaptor;

    @Test
    void shouldPersistNormalizedDistributionListAndReturnNormalizedResultWhenInputIsValid() {
        // Arrange
        Set<DistributionListTarget> mutableTargets = new HashSet<>();
        mutableTargets.add(new DistributionListTarget(DistributionChannel.EMAIL, "  qa@example.com  "));
        UpsertDistributionListCommand command = new UpsertDistributionListCommand(
                "  customer-1  ",
                mutableTargets,
                "tester"
        );

        // Act
        UpsertDistributionListResult result = service.upsert(command);
        mutableTargets.add(new DistributionListTarget(DistributionChannel.SLACK, "#alerts"));

        // Assert
        verify(distributionListPort).replaceForCustomer(distributionListCaptor.capture(), org.mockito.ArgumentMatchers.eq("tester"));
        DistributionList capturedList = distributionListCaptor.getValue();
        assertEquals("customer-1", capturedList.customerReference());
        assertEquals(Set.of(new DistributionListTarget(DistributionChannel.EMAIL, "qa@example.com")), capturedList.targets());

        assertEquals("customer-1", result.customerReference());
        assertEquals(Set.of(new DistributionListTarget(DistributionChannel.EMAIL, "qa@example.com")), result.targets());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenCustomerReferenceIsBlank() {
        // Arrange
        UpsertDistributionListCommand command = new UpsertDistributionListCommand(
                " ",
                Set.of(new DistributionListTarget(DistributionChannel.EMAIL, "qa@example.com")),
                "tester"
        );

        // Act + Assert
        assertThrows(IllegalArgumentException.class, () -> service.upsert(command));
        verify(distributionListPort, never()).replaceForCustomer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenTargetsAreEmpty() {
        // Arrange
        UpsertDistributionListCommand command = new UpsertDistributionListCommand(
                "customer-1",
                Set.of(),
                "tester"
        );

        // Act + Assert
        assertThrows(IllegalArgumentException.class, () -> service.upsert(command));
        verify(distributionListPort, never()).replaceForCustomer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }
}
