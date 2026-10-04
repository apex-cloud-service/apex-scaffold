package com.apex.scaffold.infrastructure.remote.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 配置中心连接凭据和跨系统可信身份映射。
 *
 * @author Apex Scaffold Team
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "apex.auth-center-permission")
public class AuthCenterPermissionProperties {
    private String baseUrl;
    private String accessToken;
    private List<Mapping> mappings = List.of();

    public Mapping resolve(String tenant, Long user) {
        if (tenant == null || tenant.isBlank() || user == null || user <= 0 || mappings == null) {
            throw new IllegalStateException("Trusted authorization mapping unavailable");
        }
        List<Mapping> matches = mappings.stream()
                .filter(java.util.Objects::nonNull)
                .filter(m -> java.util.Objects.equals(m.mainTenantId(), tenant)
                        && java.util.Objects.equals(m.mainUserId(), user))
                .toList();
        if (tenant == null || tenant.isBlank() || user == null || matches.size() != 1) {
            throw new IllegalStateException("Trusted authorization mapping unavailable");
        }
        Mapping mapping = matches.get(0);
        if (mapping.centerTenantId() == null
                || mapping.centerTenantId().isBlank()
                || mapping.centerUserId() == null
                || mapping.centerUserId() <= 0) {
            throw new IllegalStateException("Invalid authorization mapping");
        }
        return mapping;
    }

    public record Mapping(String mainTenantId, Long mainUserId, String centerTenantId, Long centerUserId) {}
}
