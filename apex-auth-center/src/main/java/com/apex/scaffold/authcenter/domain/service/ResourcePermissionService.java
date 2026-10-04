package com.apex.scaffold.authcenter.domain.service;

import java.util.*;
import com.apex.scaffold.authcenter.application.service.UnifiedAuthorizationQueryService;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 资源级动态权限服务
 *
 * <p>实现细粒度的资源级权限控制：</p>
 * <ul>
 *   <li>支持资源所有权验证（创建者/所有者）</li>
 *   <li>支持资源共享权限（只读/编辑/管理）</li>
 *   <li>支持组织层级权限（部门/团队/项目）</li>
 *   <li>支持数据脱敏权限（查看敏感字段）</li>
 *   <li>支持时间窗口权限（临时授权）</li>
 *   <li>支持条件权限（IP限制/设备限制）</li>
 * </ul>
 *
 * <p>权限检查优先级：</p>
 * <ol>
 *   <li>超级管理员：直接通过</li>
 *   <li>资源所有者：完全权限</li>
 *   <li>显式授权：检查具体权限</li>
 *   <li>组织权限：基于层级关系</li>
 *   <li>默认拒绝</li>
 * </ol>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
public class ResourcePermissionService {

    private static final Logger log = LoggerFactory.getLogger(ResourcePermissionService.class);

    private static final String ROLE_DEPT_MANAGER = "DEPT_MANAGER";
    private static final String RESOURCE_TYPE_SENSITIVE_DATA = "sensitive-data";
    private static final String PERMISSION_MANAGE = "MANAGE";

    /**
     * Redis键前缀
     */
    private static final String REDIS_RESOURCE_OWNER = "permission:resource:owner:";

    private static final String REDIS_RESOURCE_SHARE = "permission:resource:share:";
    private static final String REDIS_TEMP_PERMISSION = "permission:temp:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final UnifiedAuthorizationQueryService authorizationQueryService;

    private String resourceKey(String prefix, String resourceType, String resourceId, String tenantId) {
        boolean tenantAware = authorizationQueryService != null;
        return prefix
                + (tenantAware && tenantId != null && !tenantId.isBlank() ? tenantId + ":" : "")
                + resourceType
                + ":"
                + resourceId;
    }

    private Mono<Boolean> checkRbacPermission(
            String userId, String resourceType, String requiredPermission, PermissionContext context) {
        if (context.isSuperAdmin()) {
            return context.getTenantId() == null || context.getTenantId().isBlank()
                    ? Mono.just(false)
                    : Mono.just(true);
        }
        if (authorizationQueryService == null) {
            return Mono.just(true);
        }
        if (context.getTenantId() == null || context.getTenantId().isBlank()) {
            return Mono.just(false);
        }
        final Long numericUserId;
        try {
            numericUserId = Long.valueOf(userId);
        } catch (NumberFormatException exception) {
            return Mono.just(false);
        }
        String permissionCode = permissionCode(resourceType, requiredPermission);
        return authorizationQueryService.query(context.getTenantId(), numericUserId)
                .map(authorization -> authorization.permissions().contains(permissionCode));
    }

    private String permissionCode(String resourceType, String requiredPermission) {
        return switch (resourceType) {
            case "session", "user-sessions" -> "session:terminate";
            case "audit-logs" -> "auth:audit:cleanup";
            case "role" -> "role:assign";
            case "mfa" -> "mfa:unlock";
            default -> resourceType + ":" + requiredPermission.toLowerCase();
        };
    }

    private boolean isRbacManagedResource(String resourceType, String requiredPermission) {
        return ("session".equals(resourceType) || "user-sessions".equals(resourceType)) && "MANAGE".equals(requiredPermission)
                || "audit-logs".equals(resourceType) && "MANAGE".equals(requiredPermission)
                || "role".equals(resourceType) && "MANAGE".equals(requiredPermission)
                || "mfa".equals(resourceType) && "MANAGE".equals(requiredPermission);
    }

    public ResourcePermissionService(ReactiveRedisTemplate<String, String> redisTemplate) {
        this(redisTemplate, null);
    }

    public ResourcePermissionService(
            ReactiveRedisTemplate<String, String> redisTemplate,
            UnifiedAuthorizationQueryService authorizationQueryService) {
        this.redisTemplate = redisTemplate;
        this.authorizationQueryService = authorizationQueryService;
    }

    /**
     * 检查用户是否有资源访问权限
     *
     * @param userId 用户ID
     * @param resourceType 资源类型（document/project/dataset等）
     * @param resourceId 资源ID
     * @param requiredPermission 需要的权限（READ/WRITE/DELETE/MANAGE）
     * @param context 上下文信息（IP、设备等）
     * @return true表示有权限
     */
    public Mono<Boolean> hasPermission(
            String userId,
            String resourceType,
            String resourceId,
            String requiredPermission,
            PermissionContext context) {
        log.debug(
                "检查资源权限: userId={}, tenantId={}, type={}, resourceId={}, permission={}",
                userId,
                context.getTenantId(),
                resourceType,
                resourceId,
                requiredPermission);

        if ("audit-logs".equals(resourceType)
                && (context.getTenantId() == null || !context.getTenantId().equals(resourceId))) {
            return Mono.just(false);
        }
        Mono<Boolean> rbacPermission = checkRbacPermission(userId, resourceType, requiredPermission, context);
        return rbacPermission.flatMap(hasRbacPermission -> {
            if (!hasRbacPermission) {
                return Mono.just(false);
            }
            if (context.isSuperAdmin() || isRbacManagedResource(resourceType, requiredPermission)) {
                return Mono.just(true);
            }
            return isResourceOwner(userId, resourceType, resourceId, context.getTenantId())
                .flatMap(isOwner -> {
                    if (isOwner) {
                        log.debug("资源所有者直接通过: userId={}, resourceId={}", userId, resourceId);
                        return Mono.just(true);
                    }

                    // 3. 检查显式授权
                    return checkExplicitPermission(userId, resourceType, resourceId, requiredPermission, context.getTenantId())
                            .flatMap(hasExplicit -> {
                                if (hasExplicit) {
                                    return Mono.just(true);
                                }

                                // 4. 检查组织层级权限
                                return checkOrganizationPermission(
                                        userId, resourceType, resourceId, requiredPermission, context);
                            });
                })
                // 5. 检查临时权限
                .flatMap(hasPermission -> {
                    if (hasPermission) {
                        return Mono.just(true);
                    }
                    return checkTemporaryPermission(
                            userId, resourceType, resourceId, requiredPermission, context.getTenantId());
                })
                // 6. 检查条件权限（IP/设备限制）
                .flatMap(hasPermission -> {
                    if (!hasPermission) {
                        return Mono.just(false);
                    }
                    return validatePermissionContext(userId, resourceType, resourceId, context);
                });
        });
    }

    /**
     * 检查是否为资源所有者
     */
    public Mono<Boolean> isResourceOwner(String userId, String resourceType, String resourceId) {
        return isResourceOwner(userId, resourceType, resourceId, null);
    }

    private Mono<Boolean> isResourceOwner(
            String userId, String resourceType, String resourceId, String tenantId) {
        String key = resourceKey(REDIS_RESOURCE_OWNER, resourceType, resourceId, tenantId);

        return redisTemplate
                .opsForValue()
                .get(key)
                .map(ownerId -> ownerId.equals(userId))
                .defaultIfEmpty(false);
    }

    /**
     * 设置资源所有者
     */
    public Mono<Void> setResourceOwner(String resourceType, String resourceId, String ownerId) {
        return setResourceOwner(resourceType, resourceId, ownerId, null);
    }

    public Mono<Void> setResourceOwner(
            String resourceType, String resourceId, String ownerId, String tenantId) {
        String key = resourceKey(REDIS_RESOURCE_OWNER, resourceType, resourceId, tenantId);
        return redisTemplate.opsForValue().set(key, ownerId).then();
    }

    /**
     * 检查显式授权
     */
    private Mono<Boolean> checkExplicitPermission(
            String userId,
            String resourceType,
            String resourceId,
            String requiredPermission,
            String tenantId) {
        String key = resourceKey(REDIS_RESOURCE_SHARE, resourceType, resourceId, tenantId);

        return redisTemplate
                .opsForHash()
                .get(key, userId)
                .map(permissionStr -> {
                    Set<String> permissions = parsePermissions((String) permissionStr);
                    return hasRequiredPermission(permissions, requiredPermission);
                })
                .defaultIfEmpty(false);
    }

    /**
     * 授予资源权限
     *
     * @param resourceType 资源类型
     * @param resourceId 资源ID
     * @param targetUserId 目标用户ID
     * @param permissions 权限集合（READ/WRITE/DELETE/MANAGE）
     * @return 完成标识
     */
    public Mono<Void> grantPermission(
            String resourceType, String resourceId, String targetUserId, Set<String> permissions) {
        return grantPermission(resourceType, resourceId, targetUserId, permissions, null);
    }

    public Mono<Void> grantPermission(
            String resourceType,
            String resourceId,
            String targetUserId,
            Set<String> permissions,
            String tenantId) {
        String key = resourceKey(REDIS_RESOURCE_SHARE, resourceType, resourceId, tenantId);
        String permissionStr = String.join(",", permissions);

        log.info(
                "授予资源权限: type={}, resourceId={}, targetUserId={}, permissions={}",
                resourceType,
                resourceId,
                targetUserId,
                permissions);

        return redisTemplate.opsForHash().put(key, targetUserId, permissionStr).then();
    }

    /**
     * 撤销资源权限
     */
    public Mono<Void> revokePermission(String resourceType, String resourceId, String targetUserId) {
        return revokePermission(resourceType, resourceId, targetUserId, null);
    }

    public Mono<Void> revokePermission(
            String resourceType, String resourceId, String targetUserId, String tenantId) {
        String key = resourceKey(REDIS_RESOURCE_SHARE, resourceType, resourceId, tenantId);

        log.info("撤销资源权限: type={}, resourceId={}, targetUserId={}", resourceType, resourceId, targetUserId);

        return redisTemplate.opsForHash().remove(key, targetUserId).then();
    }

    /**
     * 获取资源的所有授权用户
     */
    public Flux<ResourcePermissionGrant> getResourcePermissions(String resourceType, String resourceId) {
        return getResourcePermissions(resourceType, resourceId, null);
    }

    public Flux<ResourcePermissionGrant> getResourcePermissions(
            String resourceType, String resourceId, String tenantId) {
        String key = resourceKey(REDIS_RESOURCE_SHARE, resourceType, resourceId, tenantId);

        return redisTemplate.opsForHash().entries(key).map(entry -> {
            String userId = (String) entry.getKey();
            String permissionStr = (String) entry.getValue();
            Set<String> permissions = parsePermissions(permissionStr);

            return new ResourcePermissionGrant(userId, resourceType, resourceId, permissions);
        });
    }

    /**
     * 授予临时权限（带过期时间）
     *
     * @param resourceType 资源类型
     * @param resourceId 资源ID
     * @param targetUserId 目标用户ID
     * @param permissions 权限集合
     * @param durationSeconds 有效期（秒）
     * @return 完成标识
     */
    public Mono<Void> grantTemporaryPermission(
            String resourceType,
            String resourceId,
            String targetUserId,
            Set<String> permissions,
            long durationSeconds) {
        return grantTemporaryPermission(
                resourceType, resourceId, targetUserId, permissions, durationSeconds, null);
    }

    public Mono<Void> grantTemporaryPermission(
            String resourceType,
            String resourceId,
            String targetUserId,
            Set<String> permissions,
            long durationSeconds,
            String tenantId) {
        String key = resourceKey(REDIS_TEMP_PERMISSION, resourceType, resourceId, tenantId)
                + ":" + targetUserId;
        String permissionStr = String.join(",", permissions);

        log.info(
                "授予临时权限: type={}, resourceId={}, targetUserId={}, permissions={}, duration={}s",
                resourceType,
                resourceId,
                targetUserId,
                permissions,
                durationSeconds);

        return redisTemplate
                .opsForValue()
                .set(key, permissionStr, java.time.Duration.ofSeconds(durationSeconds))
                .then();
    }

    /**
     * 检查临时权限
     */
    private Mono<Boolean> checkTemporaryPermission(
            String userId,
            String resourceType,
            String resourceId,
            String requiredPermission,
            String tenantId) {
        String key = resourceKey(REDIS_TEMP_PERMISSION, resourceType, resourceId, tenantId) + ":" + userId;

        return redisTemplate
                .opsForValue()
                .get(key)
                .map(permissionStr -> {
                    Set<String> permissions = parsePermissions(permissionStr);
                    return hasRequiredPermission(permissions, requiredPermission);
                })
                .defaultIfEmpty(false);
    }

    /**
     * 检查组织层级权限
     *
     * 示例场景：
     * - 部门经理可以访问部门内所有文档
     * - 项目管理员可以管理项目内所有资源
     */
    private Mono<Boolean> checkOrganizationPermission(
            String userId,
            String resourceType,
            String resourceId,
            String requiredPermission,
            PermissionContext context) {
        // TODO: 实现组织层级权限检查
        // 1. 查询用户的组织角色（部门经理、项目管理员等）
        // 2. 查询资源所属的组织单元
        // 3. 判断用户角色是否有该组织单元的权限

        if (context.getOrganizationRoles().contains(ROLE_DEPT_MANAGER)) {
            return Mono.just(false);
        }

        return Mono.just(false);
    }

    private Mono<Boolean> validatePermissionContext(
            String userId, String resourceType, String resourceId, PermissionContext context) {
        if (RESOURCE_TYPE_SENSITIVE_DATA.equals(resourceType)) {
            if (!isInternalIp(context.getClientIp())) {
                log.warn("敏感资源拒绝外网访问: userId={}, ip={}", userId, context.getClientIp());
                return Mono.just(false);
            }
        }

        return Mono.just(true);
    }

    private Set<String> parsePermissions(String permissionStr) {
        if (permissionStr == null || permissionStr.isEmpty()) {
            return Collections.emptySet();
        }
        return Arrays.stream(permissionStr.split(",")).map(String::trim).collect(Collectors.toSet());
    }

    private boolean hasRequiredPermission(Set<String> userPermissions, String requiredPermission) {
        if (userPermissions.contains(PERMISSION_MANAGE)) {
            return true;
        }

        switch (requiredPermission) {
            case "READ":
                return userPermissions.contains("READ")
                        || userPermissions.contains("WRITE")
                        || userPermissions.contains("DELETE");
            case "WRITE":
                return userPermissions.contains("WRITE") || userPermissions.contains("DELETE");
            case "DELETE":
                return userPermissions.contains("DELETE");
            case "MANAGE":
                return userPermissions.contains("MANAGE");
            default:
                return false;
        }
    }

    /**
     * 判断是否为内网IP
     */
    private boolean isInternalIp(String ip) {
        if (ip == null) {
            return false;
        }
        return "127.0.0.1".equals(ip) || ip.startsWith("10.") || ip.startsWith("172.16.") || ip.startsWith("192.168.");
    }

    /**
     * 权限上下文
     */
    public static class PermissionContext {
        private final boolean superAdmin;
        private final Set<String> organizationRoles;
        private final String clientIp;
        private final String deviceId;
        private final String tenantId;

        public PermissionContext(boolean superAdmin, Set<String> organizationRoles, String clientIp, String deviceId) {
            this(superAdmin, organizationRoles, clientIp, deviceId, null);
        }

        public PermissionContext(
                boolean superAdmin,
                Set<String> organizationRoles,
                String clientIp,
                String deviceId,
                String tenantId) {
            this.superAdmin = superAdmin;
            this.organizationRoles = organizationRoles != null ? organizationRoles : Collections.emptySet();
            this.clientIp = clientIp;
            this.deviceId = deviceId;
            this.tenantId = tenantId;
        }

        public boolean isSuperAdmin() {
            return superAdmin;
        }

        public Set<String> getOrganizationRoles() {
            return organizationRoles;
        }

        public String getClientIp() {
            return clientIp;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public String getTenantId() {
            return tenantId;
        }
    }

    /**
     * 资源权限授予记录
     */
    public static class ResourcePermissionGrant {
        private final String userId;
        private final String resourceType;
        private final String resourceId;
        private final Set<String> permissions;

        public ResourcePermissionGrant(String userId, String resourceType, String resourceId, Set<String> permissions) {
            this.userId = userId;
            this.resourceType = resourceType;
            this.resourceId = resourceId;
            this.permissions = permissions;
        }

        public String getUserId() {
            return userId;
        }

        public String getResourceType() {
            return resourceType;
        }

        public String getResourceId() {
            return resourceId;
        }

        public Set<String> getPermissions() {
            return permissions;
        }
    }
}
