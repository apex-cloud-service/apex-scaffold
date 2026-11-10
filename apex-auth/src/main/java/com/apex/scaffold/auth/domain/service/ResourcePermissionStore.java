package com.apex.scaffold.auth.domain.service;

import java.time.Duration;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 资源授权存取端口（所有者、显式授权与临时授权）。
 *
 * <p>键统一携带租户作用域，租户为空时不追加隔离维度以兼容历史无租户数据。
 * 所有查询在键不存在时返回空结果，不得降级为放行。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
public interface ResourcePermissionStore {

    /**
     * 原子读回资源所有者标识。
     *
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @param tenantId 租户标识，{@code null} 表示不带租户隔离维度
     * @return 所有者标识，键不存在时为空
     */
    Mono<String> findOwner(String resourceType, String resourceId, String tenantId);

    /**
     * 写入资源所有者。
     *
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @param ownerId 所有者用户标识
     * @param tenantId 租户标识，{@code null} 表示不带租户隔离维度
     * @return 写入完成信号
     */
    Mono<Void> saveOwner(String resourceType, String resourceId, String ownerId, String tenantId);

    /**
     * 读取某用户在该资源上的显式授权串。
     *
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @param userId 目标用户标识
     * @param tenantId 租户标识，{@code null} 表示不带租户隔离维度
     * @return 原始授权串（逗号分隔），未授权时为空
     */
    Mono<String> findExplicitPermission(String resourceType, String resourceId, String userId, String tenantId);

    /**
     * 写入或覆盖某用户的显式授权串。
     *
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @param userId 目标用户标识
     * @param permissionValue 原始授权串（逗号分隔）
     * @param tenantId 租户标识，{@code null} 表示不带租户隔离维度
     * @return 写入完成信号
     */
    Mono<Void> saveExplicitPermission(
            String resourceType, String resourceId, String userId, String permissionValue, String tenantId);

    /**
     * 撤销某用户的显式授权。
     *
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @param userId 目标用户标识
     * @param tenantId 租户标识，{@code null} 表示不带租户隔离维度
     * @return 撤销完成信号
     */
    Mono<Void> deleteExplicitPermission(String resourceType, String resourceId, String userId, String tenantId);

    /**
     * 读取资源上全部显式授权条目。
     *
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @param tenantId 租户标识，{@code null} 表示不带租户隔离维度
     * @return 用户标识到原始授权串的映射，无授权时为空流
     */
    Flux<Map.Entry<String, String>> findExplicitPermissions(String resourceType, String resourceId, String tenantId);

    /**
     * 写入带 TTL 的临时授权串。
     *
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @param userId 目标用户标识
     * @param permissionValue 原始授权串（逗号分隔）
     * @param ttl 有效期，到期自动失效
     * @param tenantId 租户标识，{@code null} 表示不带租户隔离维度
     * @return 写入完成信号
     */
    Mono<Void> saveTemporaryPermission(
            String resourceType,
            String resourceId,
            String userId,
            String permissionValue,
            Duration ttl,
            String tenantId);

    /**
     * 读取临时授权串。
     *
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @param userId 目标用户标识
     * @param tenantId 租户标识，{@code null} 表示不带租户隔离维度
     * @return 原始授权串（逗号分隔），无有效临时授权时为空
     */
    Mono<String> findTemporaryPermission(String resourceType, String resourceId, String userId, String tenantId);
}
