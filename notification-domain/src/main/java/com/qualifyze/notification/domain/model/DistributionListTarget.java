package com.qualifyze.notification.domain.model;

public record DistributionListTarget(
        DistributionChannel channel,
        String destination
) {
    static final int MAX_DESTINATION_LENGTH = 320;

    public DistributionListTarget {
        Require.present(channel, "channel");
        destination = Require.text(destination, "destination");
        if (destination.length() > MAX_DESTINATION_LENGTH) {
            throw new IllegalArgumentException("destination must not exceed " + MAX_DESTINATION_LENGTH + " characters");
        }
        if (channel == DistributionChannel.EMAIL && !isEmailAddress(destination)) {
            throw new IllegalArgumentException("destination must be a valid e-mail address for channel EMAIL");
        }
    }

    // Syntactic check only (one '@', non-empty local part and domain, no whitespace);
    // deliverability is the mail server's concern.
    private static boolean isEmailAddress(String value) {
        int at = value.indexOf('@');
        return at > 0
                && at == value.lastIndexOf('@')
                && at < value.length() - 1
                && value.chars().noneMatch(Character::isWhitespace);
    }
}
