package com.apex.scaffold.infrastructure.remote.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 配置中心连接凭据和跨系统可信身份映射。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "apex.auth-center-permission")
public class AuthCenterPermissionProperties {
    private String baseUrl;
    private String accessToken;
    private List<Mapping> mappings = List.of();

    /**
     * 解析主系统租户与用户对应的认证中心可信身份映射。
     *
     * @param tenant 主系统租户 ID
     * @param user   主系统用户 ID
     * @return 唯一匹配且有效的身份映射
     * @throws IllegalStateException 映射缺失、不唯一或无效时抛出
     */
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

    /**
     * 由受信任启动配置提供的跨系统身份映射，记录本身不执行校验。
     *
     * <p>使用前由 {@link AuthCenterPermissionProperties#resolve(String, Long)} 验证来源身份唯一性及目标身份有效性，
     * 不能将客户端任意提交的映射作为授权依据。
     *
     * @param mainTenantId 主系统租户标识
     * @param mainUserId 主系统用户标识
     * @param centerTenantId 认证中心租户标识
     * @param centerUserId 认证中心用户标识
     */
    public record Mapping(String mainTenantId, Long mainUserId, String centerTenantId, Long centerUserId) {}
}
