package com.qualifyze.notification.domain.model;

public record Site(
        String name,
        String address
) {
    public Site {
        name = Require.text(name, "site.name");
        address = Require.text(address, "site.address");
    }
}
