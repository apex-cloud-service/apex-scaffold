package com.apex.scaffold.application.service;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.enums.DataScopeType;
import com.apex.scaffold.domain.gateway.UnifiedAuthorizationGateway;
import com.apex.scaffold.domain.model.entity.PermissionEntity;
import com.apex.scaffold.domain.model.entity.RoleEntity;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 编排统一授权查询及具有独立到期时间的短期缓存。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Service
public class UnifiedAuthorizationService {
    private static final long MAX_CACHE_SECONDS = 30;
    private final UnifiedAuthorizationGateway gateway;
    private final boolean enabled;
    private final com.apex.scaffold.domain.gateway.AuthorizationCache cache;
    private final long cacheSeconds;

    /** 创建服务实例，不启用授权结果缓存。 */
    public UnifiedAuthorizationService(UnifiedAuthorizationGateway gateway, Environment environment) {
        this(gateway, environment, null);
    }

    /**
     * 创建服务实例并初始化授权短期缓存配置。
     *
     * @param gateway 统一授权查询网关
     * @param environment Spring 环境配置
     * @param cache 授权结果缓存，为 null 时不启用缓存
     * @throws IllegalArgumentException 缓存 TTL 配置小于 0 或超过 30 秒
     */
    @org.springframework.beans.factory.annotation.Autowired
    public UnifiedAuthorizationService(
            UnifiedAuthorizationGateway gateway,
            Environment environment,
            com.apex.scaffold.domain.gateway.AuthorizationCache cache) {
        this.gateway = gateway;
        this.cache = cache;
        this.cacheSeconds = environment.getProperty("apex.auth-center-permission.cache-seconds", Long.class, 0L);
        if (cacheSeconds < 0 || cacheSeconds > MAX_CACHE_SECONDS) {
            throw new IllegalArgumentException("Authorization cache TTL must be between 0 and 30 seconds");
        }
        this.enabled = environment.getProperty("apex.features.auth-center-permission", Boolean.class, false);
    }

    /**
     * 返回认证中心权限功能开关是否启用。
     *
     * @return 功能启用时返回 true，未配置时默认关闭
     */
    public boolean enabled() {
        return enabled;
    }

    private Mono<UnifiedAuthorizationGateway.Authorization> query(Long userId) {
        return Mono.deferContextual(context -> {
            String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            if (tenant == null || tenant.isBlank()) {
                return Mono.error(new IllegalStateException("Trusted tenant required"));
            }
            if (userId == null || userId <= 0) {
                return Mono.error(new IllegalStateException("Trusted user required"));
            }
            if (cacheSeconds == 0 || cache == null) {
                return loadAuthorization(tenant, userId);
            }
            String key = "auth-center:authorization:v1:"
                    + java.util.Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(tenant.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    + ":" + userId;
            return cache.get(key, CachedAuthorization.class)
                    .filter(value -> value.expiresAt() > System.currentTimeMillis()
                            && validAuthorization(value.authorization(), tenant, userId))
                    .map(CachedAuthorization::authorization)
                    .switchIfEmpty(
                            Mono.defer(() -> loadAuthorization(tenant, userId).flatMap(data -> cache.set(
                                            key,
                                            new CachedAuthorization(
                                                    data, System.currentTimeMillis() + cacheSeconds * 1000),
                                            java.time.Duration.ofSeconds(cacheSeconds))
                                    .thenReturn(data))));
        });
    }

    private Mono<UnifiedAuthorizationGateway.Authorization> loadAuthorization(String tenant, Long userId) {
        return Mono.defer(() -> gateway.query(tenant, userId))
                .switchIfEmpty(Mono.error(new IllegalStateException("Empty authorization response")))
                .map(data -> {
                    if (!validAuthorization(data, tenant, userId)) {
                        throw new IllegalStateException("Invalid authorization response");
                    }
                    return new UnifiedAuthorizationGateway.Authorization(
                            tenant, userId, List.copyOf(data.permissions()), List.copyOf(data.roles()));
                });
    }

    private boolean validAuthorization(UnifiedAuthorizationGateway.Authorization data, String tenant, Long userId) {
        return data != null
                && tenant.equals(data.tenantId())
                && userId.equals(data.userId())
                && data.permissions() != null
                && data.roles() != null
                && data.permissions().stream().allMatch(code -> code != null && !code.isBlank())
                && data.roles().stream()
                        .allMatch(role -> role != null
                                && role.code() != null
                                && !role.code().isBlank()
                                && role.dataScope() != null
                                && !role.dataScope().isBlank());
    }

    /**
     * 查询用户的权限列表。
     *
     * <p>经统一授权查询获取权限编码，转换为权限实体列表。</p>
     *
     * @param userId 用户 ID
     * @return 权限实体列表
     */
    public Mono<List<PermissionEntity>> permissions(Long userId) {
        return query(userId).map(data -> data.permissions().stream()
                .map(code -> {
                    PermissionEntity entity = new PermissionEntity();
                    entity.setCode(code);
                    entity.setName(code);
                    return entity;
                })
                .toList());
    }

    /**
     * 查询用户的角色列表。
     *
     * <p>经统一授权查询获取角色及数据权限范围，转换为角色实体列表。</p>
     *
     * @param userId 用户 ID
     * @return 角色实体列表
     */
    public Mono<List<RoleEntity>> roles(Long userId) {
        return query(userId).map(data -> data.roles().stream()
                .map(role -> {
                    RoleEntity entity = new RoleEntity();
                    entity.setCode(role.code());
                    entity.setName(role.code());
                    entity.setStatus(1);
                    DataScopeType scope = DataScopeType.fromName(role.dataScope());
                    if (scope == null || scope == DataScopeType.CUSTOM) {
                        throw new IllegalStateException("Unmapped central data scope");
                    }
                    entity.setDataScope(scope);
                    return entity;
                })
                .toList());
    }

    public record CachedAuthorization(UnifiedAuthorizationGateway.Authorization authorization, long expiresAt) {}
}
