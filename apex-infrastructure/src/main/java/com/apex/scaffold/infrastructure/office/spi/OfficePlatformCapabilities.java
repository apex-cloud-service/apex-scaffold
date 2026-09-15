package com.apex.scaffold.infrastructure.office.spi;

import java.util.Set;

public record OfficePlatformCapabilities(Set<String> capabilities) {
    public OfficePlatformCapabilities {
        capabilities = Set.copyOf(capabilities == null ? Set.of() : capabilities);
    }

    public boolean supports(String capability) {
        return capabilities.contains(capability);
    }
}
