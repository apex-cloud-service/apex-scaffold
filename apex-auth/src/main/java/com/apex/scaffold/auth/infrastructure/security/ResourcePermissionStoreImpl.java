package com.apex.scaffold.auth.infrastructure.security;

import com.apex.scaffold.auth.domain.service.ResourcePermissionStore;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 资源授权存取实现（Redis）。
 *
 * <p>所有者使用字符串键，显式授权使用哈希（字段为用户标识），临时授权使用带 TTL 的字符串键。
 * 键统一携带租户作用域，租户为空时不追加隔离维度以兼容历史无租户数据。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class ResourcePermissionStoreImpl implements ResourcePermissionStore {

    private static final String OWNER_KEY_PREFIX = "permission:resource:owner:";

    private static final String SHARE_KEY_PREFIX = "permission:resource:share:";

    private static final String TEMPORARY_KEY_PREFIX = "permission:temp:";

    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<String> findOwner(String resourceType, String resourceId, String tenantId) {
        return redisTemplate.opsForValue().get(resourceKey(OWNER_KEY_PREFIX, resourceType, resourceId, tenantId));
    }

    @Override
    public Mono<Void> saveOwner(String resourceType, String resourceId, String ownerId, String tenantId) {
        return redisTemplate
                .opsForValue()
                .set(resourceKey(OWNER_KEY_PREFIX, resourceType, resourceId, tenantId), ownerId)
                .then();
    }

    @Override
    public Mono<String> findExplicitPermission(String resourceType, String resourceId, String userId, String tenantId) {
        return redisTemplate
                .opsForHash()
                .get(resourceKey(SHARE_KEY_PREFIX, resourceType, resourceId, tenantId), userId)
                .map(String::valueOf);
    }

    @Override
    public Mono<Void> saveExplicitPermission(
            String resourceType, String resourceId, String userId, String permissionValue, String tenantId) {
        return redisTemplate
                .opsForHash()
                .put(resourceKey(SHARE_KEY_PREFIX, resourceType, resourceId, tenantId), userId, permissionValue)
                .then();
    }

    @Override
    public Mono<Void> deleteExplicitPermission(String resourceType, String resourceId, String userId, String tenantId) {
        return redisTemplate
                .opsForHash()
                .remove(resourceKey(SHARE_KEY_PREFIX, resourceType, resourceId, tenantId), userId)
                .then();
    }

    @Override
    public Flux<Map.Entry<String, String>> findExplicitPermissions(
            String resourceType, String resourceId, String tenantId) {
        return redisTemplate
                .opsForHash()
                .entries(resourceKey(SHARE_KEY_PREFIX, resourceType, resourceId, tenantId))
                .map(entry -> Map.entry(String.valueOf(entry.getKey()), String.valueOf(entry.getValue())));
    }

    @Override
    public Mono<Void> saveTemporaryPermission(
            String resourceType,
            String resourceId,
            String userId,
            String permissionValue,
            Duration ttl,
            String tenantId) {
        return redisTemplate
                .opsForValue()
                .set(temporaryKey(resourceType, resourceId, userId, tenantId), permissionValue, ttl)
                .then();
    }

    @Override
    public Mono<String> findTemporaryPermission(
            String resourceType, String resourceId, String userId, String tenantId) {
        return redisTemplate.opsForValue().get(temporaryKey(resourceType, resourceId, userId, tenantId));
    }

    private String temporaryKey(String resourceType, String resourceId, String userId, String tenantId) {
        return resourceKey(TEMPORARY_KEY_PREFIX, resourceType, resourceId, tenantId) + ":" + userId;
    }

    private String resourceKey(String prefix, String resourceType, String resourceId, String tenantId) {
        String tenantSegment = tenantId != null && !tenantId.isBlank() ? tenantId + ":" : "";
        return prefix + tenantSegment + resourceType + ":" + resourceId;
    }
}
