package com.qualifyze.notification.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SiteTest {

    @Test
    void shouldCreateSiteWhenNameAndAddressAreValid() {
        // Arrange + Act
        Site site = new Site("Plant A", "Street 1");

        // Assert
        assertEquals("Plant A", site.name());
        assertEquals("Street 1", site.address());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenNameIsNull() {
        // Arrange + Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new Site(
                null,
                "Street 1"
        ));

        // Assert
        assertEquals("site.name is required", exception.getMessage());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenAddressIsNull() {
        // Arrange + Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new Site(
                "Plant A",
                null
        ));

        // Assert
        assertEquals("site.address is required", exception.getMessage());
    }
}
