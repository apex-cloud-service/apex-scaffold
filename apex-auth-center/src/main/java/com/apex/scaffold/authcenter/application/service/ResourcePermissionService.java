package com.apex.scaffold.authcenter.application.service;

import com.apex.scaffold.authcenter.domain.permission.ResourcePermission;
import java.time.Duration;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 动态资源级权限服务
 *
 * 功能：
 * 1. 基于资源的细粒度权限控制
 * 2. 支持SpEL表达式的动态权限规则
 * 3. 支持权限缓存提升性能
 * 4. 支持权限继承和聚合
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Service
public class ResourcePermissionService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ExpressionParser expressionParser;

    private static final String PERMISSION_CACHE_PREFIX = "resource:permission:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    /**
     * 构造资源权限服务。
     *
     * @param redisTemplate Redis 响应式模板，用于存储资源-用户授权关系
     */
    public ResourcePermissionService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.expressionParser = new SpelExpressionParser();
    }

    /**
     * 检查用户是否有权限访问资源
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param resourceType 资源类型
     * @param resourceId 资源ID
     * @param action 操作类型
     * @param context 权限评估上下文
     * @return 是否有权限
     */
    public Mono<Boolean> hasPermission(
            String userId,
            String tenantId,
            String resourceType,
            String resourceId,
            String action,
            Map<String, Object> context) {

        // 1. 先检查缓存
        String cacheKey = buildCacheKey(userId, resourceType, resourceId, action);
        return redisTemplate
                .hasKey(cacheKey)
                .flatMap(cached -> {
                    if (cached) {
                        return redisTemplate.opsForValue().get(cacheKey).map(Boolean::parseBoolean);
                    }

                    // 2. 查询用户的权限规则
                    return getUserPermissions(userId, tenantId)
                            .filter(permission -> permission.matches(resourceType, resourceId, action))
                            .collectList()
                            .flatMap(permissions -> {
                                if (permissions.isEmpty()) {
                                    return cachePermissionResult(cacheKey, false);
                                }

                                // 3. 按优先级排序
                                permissions.sort(Comparator.comparing(
                                                ResourcePermission::getPriority,
                                                Comparator.nullsLast(Comparator.naturalOrder()))
                                        .reversed());

                                // 4. 评估权限条件
                                return evaluatePermissions(permissions, context)
                                        .flatMap(hasAccess -> cachePermissionResult(cacheKey, hasAccess));
                            });
                })
                .doOnNext(hasAccess -> {
                    if (hasAccess) {
                        log.debug(
                                "权限检查通过: userId={}, resource={}:{}, action={}",
                                userId,
                                resourceType,
                                resourceId,
                                action);
                    } else {
                        log.warn(
                                "权限检查失败: userId={}, resource={}:{}, action={}",
                                userId,
                                resourceType,
                                resourceId,
                                action);
                    }
                });
    }

    /**
     * 获取用户的所有权限
     */
    public Flux<ResourcePermission> getUserPermissions(String userId, String tenantId) {
        // 实际项目中应该从数据库查询
        // 这里返回示例权限规则
        return Flux.fromIterable(getExamplePermissions(userId, tenantId));
    }

    /**
     * 添加权限规则
     */
    public Mono<Void> addPermission(ResourcePermission permission) {
        // 实际项目中应该保存到数据库
        log.info("添加权限规则: {}", permission);
        return Mono.empty();
    }

    /**
     * 删除权限规则
     */
    public Mono<Void> removePermission(String resourceType, String resourceId, String action) {
        // 实际项目中应该从数据库删除
        log.info("删除权限规则: resource={}:{}, action={}", resourceType, resourceId, action);
        return Mono.empty();
    }

    /**
     * 清除用户权限缓存
     */
    public Mono<Void> clearUserPermissionCache(String userId) {
        String pattern = PERMISSION_CACHE_PREFIX + userId + ":*";
        return redisTemplate
                .keys(pattern)
                .flatMap(redisTemplate::delete)
                .then()
                .doOnSuccess(v -> log.info("已清除用户权限缓存: userId={}", userId));
    }

    /**
     * 批量检查权限
     */
    public Mono<Map<String, Boolean>> batchCheckPermissions(
            String userId, String tenantId, List<PermissionCheck> checks, Map<String, Object> context) {

        return Flux.fromIterable(checks)
                .flatMap(check -> hasPermission(
                                userId,
                                tenantId,
                                check.getResourceType(),
                                check.getResourceId(),
                                check.getAction(),
                                context)
                        .map(hasAccess -> Map.entry(check.getKey(), hasAccess)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    /**
     * 获取用户可访问的资源列表
     */
    public Flux<String> getAccessibleResources(
            String userId,
            String tenantId,
            String resourceType,
            String action,
            List<String> resourceIds,
            Map<String, Object> context) {

        return Flux.fromIterable(resourceIds)
                .filterWhen(resourceId -> hasPermission(userId, tenantId, resourceType, resourceId, action, context));
    }

    // ========== 私有方法 ==========

    /**
     * 评估权限条件
     */
    private Mono<Boolean> evaluatePermissions(List<ResourcePermission> permissions, Map<String, Object> context) {

        return Mono.fromCallable(() -> {
            EvaluationContext evalContext = new StandardEvaluationContext();
            context.forEach(evalContext::setVariable);

            for (ResourcePermission permission : permissions) {
                String condition = permission.getCondition();

                if (condition == null || condition.isEmpty()) {
                    // 无条件权限，直接通过
                    return true;
                }

                try {
                    Expression expression = expressionParser.parseExpression(condition);
                    Boolean result = expression.getValue(evalContext, Boolean.class);

                    if (Boolean.TRUE.equals(result)) {
                        return true;
                    }
                } catch (Exception e) {
                    log.error("权限条件评估失败: condition={}, error={}", condition, e.getMessage());
                }
            }

            return false;
        });
    }

    /**
     * 缓存权限检查结果
     */
    private Mono<Boolean> cachePermissionResult(String cacheKey, boolean hasAccess) {
        return redisTemplate
                .opsForValue()
                .set(cacheKey, String.valueOf(hasAccess), CACHE_TTL)
                .thenReturn(hasAccess);
    }

    /**
     * 构建缓存Key
     */
    private String buildCacheKey(String userId, String resourceType, String resourceId, String action) {
        return String.format("%s%s:%s:%s:%s", PERMISSION_CACHE_PREFIX, userId, resourceType, resourceId, action);
    }

    /**
     * 获取示例权限（实际应从数据库加载）
     */
    private List<ResourcePermission> getExamplePermissions(String userId, String tenantId) {
        return Arrays.asList(
                // 用户可以读取自己的订单
                ResourcePermission.builder()
                        .resourceType("ORDER")
                        .action("READ")
                        .condition("#resource.userId == #authentication.userId")
                        .scope(ResourcePermission.PermissionScope.PERSONAL)
                        .priority(100)
                        .build(),

                // 用户可以编辑自己的订单
                ResourcePermission.builder()
                        .resourceType("ORDER")
                        .action("WRITE")
                        .condition("#resource.userId == #authentication.userId && #resource.status == 'DRAFT'")
                        .scope(ResourcePermission.PermissionScope.PERSONAL)
                        .priority(100)
                        .build(),

                // 部门经理可以审批本部门的订单
                ResourcePermission.builder()
                        .resourceType("ORDER")
                        .action("APPROVE")
                        .condition("#resource.departmentId == #authentication.departmentId")
                        .scope(ResourcePermission.PermissionScope.DEPARTMENT)
                        .allowedRoles(Set.of("DEPARTMENT_MANAGER"))
                        .priority(200)
                        .build(),

                // 管理员拥有全部权限
                ResourcePermission.builder()
                        .resourceType("*")
                        .action("*")
                        .condition(null)
                        .scope(ResourcePermission.PermissionScope.GLOBAL)
                        .allowedRoles(Set.of("ADMIN", "SUPER_ADMIN"))
                        .priority(1000)
                        .build());
    }

    /**
     * 权限检查请求
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class PermissionCheck {
        private String resourceType;
        private String resourceId;
        private String action;

        public String getKey() {
            return String.format("%s:%s:%s", resourceType, resourceId, action);
        }
    }
}
