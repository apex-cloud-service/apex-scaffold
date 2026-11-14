package com.apex.scaffold.auth.openplatform;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 开放平台 scope 注册表与校验（方案 M4 / 批次 D2）。
 *
 * <p>scope 命名遵循 {@code open:<domain>:<action>}：开放应用在注册时声明其可申请的 scope 集合，
 * 令牌签发时按客户端允许列表校验（RFC 6749 §3.3 的 scope 收窄语义）。
 *
 * <p>本类提供**平台级 scope 目录**（可申请的完整集合）与**服务侧映射**（scope → 所需权限码），
 * 供网关/服务做 scope 断言与开放路由挂载。新增能力只需在目录登记，不改校验逻辑。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public final class OpenAppScopes {

    /** 文件读取 */
    public static final String FILE_READ = "open:file:read";

    /** 文件写入 */
    public static final String FILE_WRITE = "open:file:write";

    /** 工作流读取 */
    public static final String WORKFLOW_READ = "open:workflow:read";

    /** 工作流发起 */
    public static final String WORKFLOW_WRITE = "open:workflow:write";

    /** 用户只读 */
    public static final String USER_READ = "open:user:read";

    /** 站内信发送 */
    public static final String MESSAGE_SEND = "open:message:send";

    /** 平台可申请的完整 scope 目录（开放平台注册时校验）。 */
    private static final Set<String> CATALOG =
            new LinkedHashSet<>(Set.of(FILE_READ, FILE_WRITE, WORKFLOW_READ, WORKFLOW_WRITE, USER_READ, MESSAGE_SEND));

    /** scope → 服务侧所需权限码映射（开放路由挂载点断言用）。 */
    private static final java.util.Map<String, String> PERMISSION_MAPPING = java.util.Map.of(
            FILE_READ, "file:read",
            FILE_WRITE, "file:write",
            WORKFLOW_READ, "workflow:read",
            WORKFLOW_WRITE, "workflow:write",
            USER_READ, "user:read",
            MESSAGE_SEND, "office:message:send");

    private OpenAppScopes() {}

    /**
     * 平台 scope 目录（不可变快照）。
     *
     * @return 全部可申请 scope
     */
    public static Set<String> catalog() {
        return Set.copyOf(CATALOG);
    }

    /**
     * 校验申请 scope 是否全部在目录内。
     *
     * @param scopes 申请的 scope 集合（可空）
     * @return 非法 scope 列表（空表示全部合法）
     */
    public static java.util.List<String> invalidScopes(java.util.Collection<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return java.util.List.of();
        }
        return scopes.stream().filter(scope -> !CATALOG.contains(scope)).toList();
    }

    /**
     * scope 对应的服务侧权限码。
     *
     * @param scope 开放 scope
     * @return 权限码；未映射时为 null
     */
    public static String permissionFor(String scope) {
        return PERMISSION_MAPPING.get(scope);
    }

    /**
     * 判断开放应用声明的 scope 是否覆盖请求 scope（请求不得超出授权范围）。
     *
     * @param granted 应用已授权 scope
     * @param requested 本次请求 scope
     * @return 是否全部覆盖
     */
    public static boolean covers(java.util.Collection<String> granted, java.util.Collection<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return true;
        }
        if (granted == null) {
            return false;
        }
        return granted.containsAll(requested);
    }
}
