package com.qualifyze.notification.adapter.rest.api.distributionlist;

import com.qualifyze.notification.adapter.rest.api.contract.model.DistributionListChannel;
import com.qualifyze.notification.adapter.rest.api.contract.model.DistributionListTargetRequest;
import com.qualifyze.notification.adapter.rest.api.contract.model.UpsertDistributionListRequest;
import com.qualifyze.notification.application.port.in.distribution.UpsertDistributionListCommand;
import com.qualifyze.notification.domain.model.DistributionChannel;
import com.qualifyze.notification.domain.model.DistributionListTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DistributionListRequestMapperTest {

    private final DistributionListRequestMapper mapper = new DistributionListRequestMapper();

    @Test
    void shouldMapRequestIntoUseCaseCommandWhenRequestIsValid() {
        // Arrange
        DistributionListTargetRequest email = new DistributionListTargetRequest();
        email.setDeliveryChannel(DistributionListChannel.EMAIL);
        email.setNotificationTarget("ops@example.com");
        DistributionListTargetRequest slack = new DistributionListTargetRequest();
        slack.setDeliveryChannel(DistributionListChannel.SLACK);
        slack.setNotificationTarget("#ops-alerts");
        UpsertDistributionListRequest request = new UpsertDistributionListRequest();
        request.setTargets(List.of(email, slack));

        // Act
        UpsertDistributionListCommand command = mapper.toCommand("customer-1", request, "jane.doe@qualifyze.com");

        // Assert
        assertEquals("customer-1", command.customerReference());
        assertEquals("jane.doe@qualifyze.com", command.changedBy());
        assertEquals(
                Set.of(
                        new DistributionListTarget(DistributionChannel.EMAIL, "ops@example.com"),
                        new DistributionListTarget(DistributionChannel.SLACK, "#ops-alerts")
                ),
                command.targets()
        );
    }

    @Test
    void shouldCreateUnmodifiableTargetSetWithDeduplicatedValues() {
        // Arrange
        DistributionListTargetRequest duplicateA = new DistributionListTargetRequest();
        duplicateA.setDeliveryChannel(DistributionListChannel.EMAIL);
        duplicateA.setNotificationTarget("ops@example.com");
        DistributionListTargetRequest duplicateB = new DistributionListTargetRequest();
        duplicateB.setDeliveryChannel(DistributionListChannel.EMAIL);
        duplicateB.setNotificationTarget("ops@example.com");
        UpsertDistributionListRequest request = new UpsertDistributionListRequest();
        request.setTargets(List.of(duplicateA, duplicateB));

        // Act
        UpsertDistributionListCommand command = mapper.toCommand("customer-1", request, "jane.doe@qualifyze.com");

        // Assert
        assertEquals(1, command.targets().size());
        assertThrows(UnsupportedOperationException.class, () -> command.targets().add(
                new DistributionListTarget(DistributionChannel.SLACK, "#new")
        ));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenDeliveryChannelIsNull() {
        // Arrange
        DistributionListTargetRequest invalid = new DistributionListTargetRequest();
        invalid.setNotificationTarget("ops@example.com");
        UpsertDistributionListRequest request = new UpsertDistributionListRequest();
        request.setTargets(List.of(invalid));

        // Act + Assert
        assertThrows(NullPointerException.class, () -> mapper.toCommand("customer-1", request, "jane.doe@qualifyze.com"));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenNotificationTargetIsBlank() {
        // Arrange
        DistributionListTargetRequest invalid = new DistributionListTargetRequest();
        invalid.setDeliveryChannel(DistributionListChannel.EMAIL);
        invalid.setNotificationTarget(" ");
        UpsertDistributionListRequest request = new UpsertDistributionListRequest();
        request.setTargets(List.of(invalid));

        // Act + Assert
        assertThrows(IllegalArgumentException.class, () -> mapper.toCommand("customer-1", request, "jane.doe@qualifyze.com"));
    }
}
