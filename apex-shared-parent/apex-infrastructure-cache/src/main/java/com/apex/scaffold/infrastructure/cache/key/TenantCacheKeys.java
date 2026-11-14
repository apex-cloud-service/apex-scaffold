package com.apex.scaffold.infrastructure.cache.key;

import com.apex.scaffold.common.context.TenantContext;

/**
 * 租户化缓存键助手（方案批次 B9）。
 *
 * <p>统一缓存键规则：{@code <prefix>:tenant:<tenantCode>:<segments...>}，杜绝各模块
 * 自行拼接导致的键空间漂移与跨租户串读。租户编码取可信上下文（{@link TenantContext}），
 * 上下文缺失时 fail-closed 抛异常，不允许生成无租户维度的键。
 *
 * <p>约定：租户相关缓存键必须经本类构造；平台级（跨租户）缓存需显式调用
 * {@link #platform(String, Object...)} 并带 {@code platform} 段，便于审计识别。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public final class TenantCacheKeys {

    /** 租户维度段名 */
    private static final String TENANT_SEGMENT = "tenant";

    /** 平台维度段名（跨租户显式声明） */
    private static final String PLATFORM_SEGMENT = "platform";

    private TenantCacheKeys() {}

    /**
     * 构造当前租户的缓存键。
     *
     * @param prefix   业务前缀（如 {@code auth:permissions}）
     * @param segments 业务分段（如 userId、jti），可为空
     * @return 形如 {@code prefix:tenant:<code>:seg1:seg2} 的缓存键
     * @throws IllegalStateException 可信租户上下文缺失（fail-closed）
     */
    public static String of(String prefix, Object... segments) {
        String tenant = TenantContext.currentTenantId().orElse(null);
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalStateException("租户上下文未装配，拒绝构造无租户维度的缓存键: prefix=" + prefix);
        }
        return build(prefix, TENANT_SEGMENT, tenant, segments);
    }

    /**
     * 构造指定租户的缓存键（用于消费侧按消息中的租户还原键）。
     *
     * @param prefix     业务前缀
     * @param tenantCode 显式租户编码（须来自可信消息/令牌声明，非客户端输入）
     * @param segments   业务分段
     * @return 租户化缓存键
     * @throws IllegalArgumentException 租户编码为空白
     */
    public static String forTenant(String prefix, String tenantCode, Object... segments) {
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new IllegalArgumentException("租户编码不能为空");
        }
        return build(prefix, TENANT_SEGMENT, tenantCode, segments);
    }

    /**
     * 构造平台级（跨租户）缓存键——显式声明，便于审计与隔离检查。
     *
     * @param prefix   业务前缀
     * @param segments 业务分段
     * @return 形如 {@code prefix:platform:seg1:seg2} 的缓存键
     */
    public static String platform(String prefix, Object... segments) {
        return build(prefix, PLATFORM_SEGMENT, null, segments);
    }

    /**
     * 校验既有缓存键是否携带租户或平台维度段（用于架构测试与迁移审计）。
     *
     * @param key 缓存键
     * @return 合规时为 true
     */
    public static boolean isTenantScoped(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String[] parts = key.split(":");
        for (int i = 1; i < parts.length; i++) {
            if (TENANT_SEGMENT.equals(parts[i]) || PLATFORM_SEGMENT.equals(parts[i])) {
                return true;
            }
        }
        return false;
    }

    private static String build(String prefix, String dimension, String tenantCode, Object... segments) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("缓存键前缀不能为空");
        }
        StringBuilder sb = new StringBuilder(prefix).append(':').append(dimension);
        if (tenantCode != null) {
            sb.append(':').append(tenantCode);
        }
        if (segments != null) {
            for (Object segment : segments) {
                if (segment != null && !String.valueOf(segment).isBlank()) {
                    sb.append(':').append(segment);
                }
            }
        }
        return sb.toString();
    }
}
