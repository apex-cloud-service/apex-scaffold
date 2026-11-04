package com.apex.scaffold.common.openapi;

import io.jsonwebtoken.Claims;

/**
 * 开放应用令牌的判定与主体解析（服务端纵深防御）。
 *
 * <p>网关已验签并注入可信头，但服务端仍必须独立识别应用令牌，不能让应用令牌冒用
 * 普通用户身份。只有同时满足以下条件才视为开放应用：
 * <ul>
 *   <li>路径以 {@code /open-api/} 开头；</li>
 *   <li>携带网关能力标记头（说明经过网关开放链路）；</li>
 *   <li>令牌含 {@code client_id}，且 {@code token_use=access}；</li>
 *   <li>{@code grant_type} 为 {@code client_credentials} 或 {@code authorization_code}；</li>
 *   <li>不含 {@code userId}（个人令牌不得冒充应用）。</li>
 * </ul>
 *
 * <p>主体账号来自令牌 {@code sub_uid}；缺失时不允许访问需要用户属主的开放写端点。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public final class OpenApiTokenSupport {

    /** 开放接口前缀。 */
    public static final String OPEN_API_PREFIX = "/open-api/";

    /** 网关能力标记头。 */
    public static final String CAPABILITY_HEADER = "X-Open-Api-Capability";

    private OpenApiTokenSupport() {}

    /**
     * 判断当前请求是否为服务端应识别的开放应用调用。
     *
     * @param path 请求路径
     * @param capability 能力标记头
     * @param claims 已验签声明
     * @return 是开放应用时返回 true
     */
    public static boolean isOpenApplication(String path, String capability, Claims claims) {
        if (path == null
                || !path.startsWith(OPEN_API_PREFIX)
                || capability == null
                || capability.isBlank()
                || claims == null) {
            return false;
        }
        if (claims.get("userId") != null) {
            return false;
        }
        if (claim(claims, "client_id") == null) {
            return false;
        }
        if (!"access".equals(claim(claims, "token_use"))) {
            return false;
        }
        String grant = claim(claims, "grant_type");
        return "client_credentials".equals(grant) || "authorization_code".equals(grant);
    }

    /**
     * 解析应用令牌绑定的主体用户。
     *
     * @param claims 已验签声明
     * @return 主体用户 ID；未绑定返回 null
     */
    public static Long subjectUserId(Claims claims) {
        String raw = claim(claims, "sub_uid");
        if (raw == null) {
            return null;
        }
        try {
            long value = Long.parseLong(raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    /**
     * 应用租户声明（开放令牌使用下划线字段）。
     *
     * @param claims 已验签声明
     * @return 租户编码；缺失返回 null
     */
    public static String tenantId(Claims claims) {
        return claim(claims, "tenant_id");
    }

    /**
     * 应用客户端标识。
     *
     * @param claims 已验签声明
     * @return client_id；缺失返回 null
     */
    public static String clientId(Claims claims) {
        return claim(claims, "client_id");
    }

    private static String claim(Claims claims, String name) {
        Object value = claims.get(name);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
