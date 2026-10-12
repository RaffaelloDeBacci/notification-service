package com.qualifyze.notification.domain.model;

public enum DistributionChannel {
    EMAIL,
    SLACK;

    public static DistributionChannel from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        return DistributionChannel.valueOf(value.strip().toUpperCase());
    }
}
