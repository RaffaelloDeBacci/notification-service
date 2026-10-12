package com.qualifyze.notification.domain.model;

import java.util.Set;

/** Guards for domain invariants; every violation is an {@link IllegalArgumentException}. */
final class Require {

    private Require() {
    }

    static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }

    static <T> T present(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    static int range(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
        }
        return value;
    }

    static <T> Set<T> notEmpty(Set<T> value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return Set.copyOf(value);
    }
}
