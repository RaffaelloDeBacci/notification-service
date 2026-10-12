package com.qualifyze.notification.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DistributionModelTest {

    @Test
    void shouldCreateDistributionListWithNormalizedCustomerReferenceAndImmutableTargets() {
        // Arrange
        Set<DistributionListTarget> targets = new HashSet<>();
        targets.add(new DistributionListTarget(DistributionChannel.EMAIL, "ops@example.com"));

        // Act
        DistributionList result = new DistributionList("  customer-1  ", targets);
        targets.add(new DistributionListTarget(DistributionChannel.SLACK, "#alerts"));

        // Assert
        assertEquals("customer-1", result.customerReference());
        assertEquals(Set.of(new DistributionListTarget(DistributionChannel.EMAIL, "ops@example.com")), result.targets());
        assertThrows(UnsupportedOperationException.class, () -> result.targets().add(
                new DistributionListTarget(DistributionChannel.SLACK, "#other")
        ));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDistributionListCustomerReferenceIsBlank() {
        // Arrange + Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new DistributionList(
                " ",
                Set.of(new DistributionListTarget(DistributionChannel.EMAIL, "ops@example.com"))
        ));

        // Assert
        assertEquals("customerReference is required", exception.getMessage());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDistributionListCustomerReferenceIsNull() {
        // Arrange + Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new DistributionList(
                null,
                Set.of(new DistributionListTarget(DistributionChannel.EMAIL, "ops@example.com"))
        ));

        // Assert
        assertEquals("customerReference is required", exception.getMessage());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDistributionListTargetsAreEmpty() {
        // Arrange + Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new DistributionList(
                "customer-1",
                Set.of()
        ));

        // Assert
        assertEquals("targets is required", exception.getMessage());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDistributionListTargetsAreNull() {
        // Arrange + Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new DistributionList(
                "customer-1",
                null
        ));

        // Assert
        assertEquals("targets is required", exception.getMessage());
    }

    @Test
    void shouldCreateDistributionListTargetWithTrimmedDestination() {
        // Arrange + Act
        DistributionListTarget result = new DistributionListTarget(DistributionChannel.EMAIL, "  qa@example.com  ");

        // Assert
        assertEquals(DistributionChannel.EMAIL, result.channel());
        assertEquals("qa@example.com", result.destination());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDistributionChannelIsNullInDistributionListTarget() {
        // Arrange + Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new DistributionListTarget(
                null,
                "qa@example.com"
        ));

        // Assert
        assertEquals("channel is required", exception.getMessage());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDestinationIsBlankInDistributionListTarget() {
        // Arrange + Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new DistributionListTarget(
                DistributionChannel.EMAIL,
                " "
        ));

        // Assert
        assertEquals("destination is required", exception.getMessage());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDestinationIsNullInDistributionListTarget() {
        // Arrange + Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new DistributionListTarget(
                DistributionChannel.EMAIL,
                null
        ));

        // Assert
        assertEquals("destination is required", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ops", "@example.com", "ops@", "a@b@example.com", "ops team@example.com"})
    void shouldRejectInvalidEmailDestination(String destination) {
        // Arrange + Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new DistributionListTarget(
                DistributionChannel.EMAIL,
                destination
        ));

        // Assert
        assertEquals("destination must be a valid e-mail address for channel EMAIL", exception.getMessage());
    }

    @Test
    void shouldAcceptNonEmailDestinationForSlackChannel() {
        // Arrange + Act
        DistributionListTarget result = new DistributionListTarget(DistributionChannel.SLACK, "#alerts");

        // Assert
        assertEquals("#alerts", result.destination());
    }

    @Test
    void shouldAcceptDestinationAtMaximumLengthAndRejectOneCharacterMore() {
        // Arrange
        String domain = "@example.com";
        String atMax = "a".repeat(DistributionListTarget.MAX_DESTINATION_LENGTH - domain.length()) + domain;
        String overMax = "a" + atMax;

        // Act
        DistributionListTarget accepted = new DistributionListTarget(DistributionChannel.EMAIL, atMax);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new DistributionListTarget(
                DistributionChannel.EMAIL,
                overMax
        ));

        // Assert
        assertEquals(atMax, accepted.destination());
        assertEquals("destination must not exceed 320 characters", exception.getMessage());
    }

    @Test
    void shouldReturnDistributionChannelWhenInputContainsWhitespaceAndDifferentCase() {
        // Arrange + Act
        DistributionChannel result = DistributionChannel.from("  email  ");

        // Assert
        assertEquals(DistributionChannel.EMAIL, result);
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDistributionChannelFromInputIsBlank() {
        // Arrange + Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> DistributionChannel.from(" "));

        // Assert
        assertEquals("channel is required", exception.getMessage());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDistributionChannelFromInputIsNull() {
        // Arrange + Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> DistributionChannel.from(null));

        // Assert
        assertEquals("channel is required", exception.getMessage());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDistributionChannelFromInputIsUnknown() {
        // Arrange + Act + Assert
        assertThrows(IllegalArgumentException.class, () -> DistributionChannel.from("fax"));
    }
}
