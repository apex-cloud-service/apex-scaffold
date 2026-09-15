package com.apex.scaffold.infrastructure.office.config;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "apex.office")
public class OfficePlatformProperties {
    private String loginTenantId;
    private boolean enabled;
    private Duration timeout = Duration.ofSeconds(5);
    private int maxRetries = 2;
    private Map<com.apex.scaffold.domain.office.OfficePlatform, Platform> platforms =
            new EnumMap<>(com.apex.scaffold.domain.office.OfficePlatform.class);

    @Data
    public static class Platform {
        private boolean enabled;
        private String appId;
        private String secretReference;
        private String redirectUri;
        private String signingSecretReference;
        private java.util.Set<String> scopes = new java.util.HashSet<>();
    }
}
