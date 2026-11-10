package com.apex.scaffold.auth.domain.service;

import com.apex.scaffold.auth.domain.repository.AuthorizationQueryPort;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 资源级动态权限服务
 *
 * <p>实现细粒度的资源级权限控制：</p>
 * <ul>
 *   <li>支持资源所有权验证（创建者/所有者）</li>
 *   <li>支持资源共享权限（只读/编辑/管理）</li>
 *   <li>组织层级权限当前未实现，检查组织权限时默认拒绝</li>
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
 *   <li>组织权限：当前默认拒绝，待接入可靠的组织权限端口</li>
 *   <li>默认拒绝</li>
 * </ol>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Slf4j
public class ResourcePermissionService {

    private static final String RESOURCE_TYPE_SENSITIVE_DATA = "sensitive-data";
    private static final String PERMISSION_MANAGE = "MANAGE";
    private static final String RESOURCE_TYPE_AUDIT_LOGS = "audit-logs";

    private final ResourcePermissionStore permissionStore;
    private final AuthorizationQueryPort authorizationQueryPort;

    private Mono<Boolean> checkRbacPermission(
            String userId, String resourceType, String requiredPermission, PermissionContext context) {
        if (context.isSuperAdmin()) {
            return context.getTenantId() == null || context.getTenantId().isBlank()
                    ? Mono.just(false)
                    : Mono.just(true);
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
        return authorizationQueryPort
                .queryPermissions(context.getTenantId(), numericUserId)
                .map(permissions -> permissions.contains(permissionCode));
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
        return ("session".equals(resourceType) || "user-sessions".equals(resourceType))
                        && "MANAGE".equals(requiredPermission)
                || "audit-logs".equals(resourceType) && "MANAGE".equals(requiredPermission)
                || "role".equals(resourceType) && "MANAGE".equals(requiredPermission)
                || "mfa".equals(resourceType) && "MANAGE".equals(requiredPermission);
    }

    /**
     * 构造资源权限服务。
     *
     * @param permissionStore 资源授权存取端口，承载所有者、显式授权与临时授权
     * @param authorizationQueryPort 授权快照查询端口，用于 RBAC 权限判定
     */
    public ResourcePermissionService(
            ResourcePermissionStore permissionStore, AuthorizationQueryPort authorizationQueryPort) {
        this.permissionStore = permissionStore;
        this.authorizationQueryPort = authorizationQueryPort;
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

        boolean auditTenantMismatch =
                context.getTenantId() == null || !context.getTenantId().equals(resourceId);
        if (RESOURCE_TYPE_AUDIT_LOGS.equals(resourceType) && auditTenantMismatch) {
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
                        return checkExplicitPermission(
                                        userId, resourceType, resourceId, requiredPermission, context.getTenantId())
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

    private Mono<Boolean> isResourceOwner(String userId, String resourceType, String resourceId, String tenantId) {
        return permissionStore
                .findOwner(resourceType, resourceId, tenantId)
                .map(ownerId -> ownerId.equals(userId))
                .defaultIfEmpty(false);
    }

    /**
     * 设置资源所有者
     */
    public Mono<Void> setResourceOwner(String resourceType, String resourceId, String ownerId) {
        return setResourceOwner(resourceType, resourceId, ownerId, null);
    }

    /**
     * 在指定租户作用域下写入资源所有者。
     *
     * @param resourceType 资源类型，参与缓存键构造
     * @param resourceId 资源标识，参与缓存键构造
     * @param ownerId 所有者用户标识
     * @param tenantId 租户标识，{@code null} 表示不带租户隔离维度
     */
    public Mono<Void> setResourceOwner(String resourceType, String resourceId, String ownerId, String tenantId) {
        return permissionStore.saveOwner(resourceType, resourceId, ownerId, tenantId);
    }

    /**
     * 检查显式授权
     */
    private Mono<Boolean> checkExplicitPermission(
            String userId, String resourceType, String resourceId, String requiredPermission, String tenantId) {
        return permissionStore
                .findExplicitPermission(resourceType, resourceId, userId, tenantId)
                .map(permissionValue -> hasRequiredPermission(parsePermissions(permissionValue), requiredPermission))
                .defaultIfEmpty(false);
    }

    /**
     * 授予资源权限
     *
     * @param resourceType 资源类型
     * @param resourceId 资源ID
     * @param targetUserId 目标用户ID
     * @param permissions 权限集合（READ/WRITE/DELETE/MANAGE）
     */
    public Mono<Void> grantPermission(
            String resourceType, String resourceId, String targetUserId, Set<String> permissions) {
        return grantPermission(resourceType, resourceId, targetUserId, permissions, null);
    }

    /**
     * 授予资源权限（指定租户）
     *
     * @param resourceType 资源类型
     * @param resourceId 资源ID
     * @param targetUserId 目标用户ID
     * @param permissions 权限集合（READ/WRITE/DELETE/MANAGE）
     * @param tenantId 租户ID，null表示默认作用域
     */
    public Mono<Void> grantPermission(
            String resourceType, String resourceId, String targetUserId, Set<String> permissions, String tenantId) {
        log.info(
                "授予资源权限: type={}, resourceId={}, targetUserId={}, permissions={}",
                resourceType,
                resourceId,
                targetUserId,
                permissions);

        return permissionStore.saveExplicitPermission(
                resourceType,
                resourceId,
                targetUserId,
                permissions.stream().sorted().collect(Collectors.joining(",")),
                tenantId);
    }

    /**
     * 撤销资源权限
     */
    public Mono<Void> revokePermission(String resourceType, String resourceId, String targetUserId) {
        return revokePermission(resourceType, resourceId, targetUserId, null);
    }

    /**
     * 撤销资源权限（指定租户）
     *
     * @param resourceType 资源类型
     * @param resourceId 资源ID
     * @param targetUserId 目标用户ID
     * @param tenantId 租户ID，null表示默认作用域
     */
    public Mono<Void> revokePermission(String resourceType, String resourceId, String targetUserId, String tenantId) {
        log.info("撤销资源权限: type={}, resourceId={}, targetUserId={}", resourceType, resourceId, targetUserId);

        return permissionStore.deleteExplicitPermission(resourceType, resourceId, targetUserId, tenantId);
    }

    /**
     * 获取资源的所有授权用户
     */
    public Flux<ResourcePermissionGrant> getResourcePermissions(String resourceType, String resourceId) {
        return getResourcePermissions(resourceType, resourceId, null);
    }

    /**
     * 在指定租户作用域下读取资源的全部显式授权。
     *
     * @param resourceType 资源类型，参与缓存键构造
     * @param resourceId 资源标识，参与缓存键构造
     * @param tenantId 租户标识，{@code null} 表示不带租户隔离维度
     * @return 授权条目流；未授权时为空流
     */
    public Flux<ResourcePermissionGrant> getResourcePermissions(
            String resourceType, String resourceId, String tenantId) {
        return permissionStore
                .findExplicitPermissions(resourceType, resourceId, tenantId)
                .map(entry -> new ResourcePermissionGrant(
                        entry.getKey(), resourceType, resourceId, parsePermissions(entry.getValue())));
    }

    /**
     * 授予临时权限（带过期时间）
     *
     * @param resourceType 资源类型
     * @param resourceId 资源ID
     * @param targetUserId 目标用户ID
     * @param permissions 权限集合
     * @param durationSeconds 有效期（秒）
     */
    public Mono<Void> grantTemporaryPermission(
            String resourceType,
            String resourceId,
            String targetUserId,
            Set<String> permissions,
            long durationSeconds) {
        return grantTemporaryPermission(resourceType, resourceId, targetUserId, permissions, durationSeconds, null);
    }

    /**
     * 授予临时权限（带过期时间，指定租户）
     *
     * @param resourceType 资源类型
     * @param resourceId 资源ID
     * @param targetUserId 目标用户ID
     * @param permissions 权限集合
     * @param durationSeconds 有效期（秒）
     * @param tenantId 租户ID，null表示默认作用域
     */
    public Mono<Void> grantTemporaryPermission(
            String resourceType,
            String resourceId,
            String targetUserId,
            Set<String> permissions,
            long durationSeconds,
            String tenantId) {
        log.info(
                "授予临时权限: type={}, resourceId={}, targetUserId={}, permissions={}, duration={}s",
                resourceType,
                resourceId,
                targetUserId,
                permissions,
                durationSeconds);

        return permissionStore.saveTemporaryPermission(
                resourceType,
                resourceId,
                targetUserId,
                String.join(",", permissions),
                java.time.Duration.ofSeconds(durationSeconds),
                tenantId);
    }

    /**
     * 检查临时权限
     */
    private Mono<Boolean> checkTemporaryPermission(
            String userId, String resourceType, String resourceId, String requiredPermission, String tenantId) {
        return permissionStore
                .findTemporaryPermission(resourceType, resourceId, userId, tenantId)
                .map(permissionValue -> hasRequiredPermission(parsePermissions(permissionValue), requiredPermission))
                .defaultIfEmpty(false);
    }

    /**
     * 检查组织层级权限。
     *
     * <p>当前实现默认拒绝。{@link PermissionContext#getOrganizationRoles()} 只是调用方
     * 传入的声明，不能单独作为授权依据；未来必须接入可靠的 {@code OrganizationPermissionPort}，
     * 校验租户、用户组织归属、资源所属组织单元与角色在该单元内的权限快照后，才可返回放行。</p>
     *
     * <p>典型目标场景为部门经理访问部门内文档、项目管理员管理项目资源；在这些能力实现前，
     * 任何组织角色声明均不得绕过资源所有者、显式授权或临时授权检查。</p>
     *
     * @param userId 用户 ID
     * @param resourceType 资源类型
     * @param resourceId 资源 ID
     * @param requiredPermission 所需权限
     * @param context 权限上下文
     * @return 恒为 {@code false}，表示组织层级授权尚未实现
     */
    private Mono<Boolean> checkOrganizationPermission(
            String userId,
            String resourceType,
            String resourceId,
            String requiredPermission,
            PermissionContext context) {
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

        return switch (requiredPermission) {
            case "READ" -> userPermissions.contains("READ")
                    || userPermissions.contains("WRITE")
                    || userPermissions.contains("DELETE");
            case "WRITE" -> userPermissions.contains("WRITE") || userPermissions.contains("DELETE");
            case "DELETE" -> userPermissions.contains("DELETE");
            case "MANAGE" -> userPermissions.contains("MANAGE");
            default -> false;
        };
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
    @lombok.Getter
    public static class PermissionContext {
        private final boolean superAdmin;
        private final Set<String> organizationRoles;
        private final String clientIp;
        private final String deviceId;
        private final String tenantId;

        /**
         * 构造权限上下文（无租户标识）。
         *
         * @param superAdmin 是否超级管理员
         * @param organizationRoles 组织角色集合
         * @param clientIp 客户端IP
         * @param deviceId 设备ID
         */
        public PermissionContext(boolean superAdmin, Set<String> organizationRoles, String clientIp, String deviceId) {
            this(superAdmin, organizationRoles, clientIp, deviceId, null);
        }

        /**
         * 构造权限上下文。
         *
         * @param superAdmin 是否超级管理员
         * @param organizationRoles 组织角色集合
         * @param clientIp 客户端IP
         * @param deviceId 设备ID
         * @param tenantId 租户ID
         */
        public PermissionContext(
                boolean superAdmin, Set<String> organizationRoles, String clientIp, String deviceId, String tenantId) {
            this.superAdmin = superAdmin;
            this.organizationRoles = organizationRoles != null ? organizationRoles : Collections.emptySet();
            this.clientIp = clientIp;
            this.deviceId = deviceId;
            this.tenantId = tenantId;
        }
    }

    /**
     * 资源权限授予记录
     */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public static class ResourcePermissionGrant {
        private final String userId;
        private final String resourceType;
        private final String resourceId;
        private final Set<String> permissions;
    }
}
