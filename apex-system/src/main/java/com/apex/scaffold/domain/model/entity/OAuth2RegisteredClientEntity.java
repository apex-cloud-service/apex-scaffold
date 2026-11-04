package com.apex.scaffold.domain.model.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import lombok.Data;

/**
 * OAuth2注册客户端领域实体
 *
 * <p>表示一个注册到授权服务器的OAuth2客户端应用。客户端可以是：
 * <ul>
 *   <li>Web应用(需要client_secret,使用授权码模式)</li>
 *   <li>移动应用(公共客户端,使用PKCE增强的授权码模式)</li>
 *   <li>服务应用(M2M,使用客户端凭证模式)</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Data
public class OAuth2RegisteredClientEntity {

    private String id;

    private String clientId;

    private Instant clientIdIssuedAt;

    private String clientSecret;

    private Instant clientSecretExpiresAt;

    private String clientName;

    private Set<String> clientAuthenticationMethods;

    private Set<String> authorizationGrantTypes;

    private Set<String> redirectUris;

    private Set<String> postLogoutRedirectUris;

    private Set<String> scopes;

    private String clientSettings;

    private String tokenSettings;

    /**
     * 上一个客户端密钥（已加密）
     *
     * <p>密钥轮换后的旧密钥，保留用于宽限期认证：客户端在轮换后的一段时间内
     * 仍可使用旧密钥完成认证，避免密钥轮换导致客户端立即失联。
     */
    private String previousClientSecret;

    /**
     * 密钥轮换时间
     *
     * <p>最近一次密钥轮换的时间戳，用于审计与轮换周期统计；从未轮换时为 {@code null}。
     */
    private Instant secretRotatedAt;

    /**
     * 密钥轮换次数
     *
     * <p>累计轮换计数，初始为 0，每次轮换递增 1。
     */
    private int secretRotationCount;

    /**
     * 执行密钥轮换
     *
     * <p>轮换语义：
     * <ol>
     *   <li>当前密钥降级为宽限期密钥（{@code previousClientSecret}）</li>
     *   <li>设置新密钥及其过期时间</li>
     *   <li>记录轮换时间并递增轮换计数</li>
     * </ol>
     *
     * @param encodedSecret 新的已加密客户端密钥（BCrypt），不允许为空
     * @param expiresAt 新密钥的过期时间，不允许为空
     */
    public void rotateSecret(String encodedSecret, Instant expiresAt) {
        this.previousClientSecret = this.clientSecret;
        this.clientSecret = encodedSecret;
        this.clientSecretExpiresAt = expiresAt;
        this.secretRotatedAt = Instant.now();
        this.secretRotationCount++;
    }

    /**
     * 判断密钥是否已过期
     *
     * @return true-密钥已过有效期，false-仍在有效期内或未设置过期时间
     */
    public boolean isSecretExpired() {
        return clientSecretExpiresAt != null && Instant.now().isAfter(clientSecretExpiresAt);
    }

    /**
     * 即将过期判定阈值（天）
     *
     * <p>与密钥轮换服务的提醒阈值保持一致（30 天），用于驱动密钥轮换提醒。
     */
    public static final long EXPIRING_SOON_THRESHOLD_DAYS = 30;

    /**
     * 判断密钥是否即将过期（30 天内）
     *
     * @return true-密钥未过期但将在 30 天内到期
     */
    public boolean isSecretExpiringSoon() {
        return clientSecretExpiresAt != null
                && !isSecretExpired()
                && Instant.now()
                        .plus(Duration.ofDays(EXPIRING_SOON_THRESHOLD_DAYS))
                        .isAfter(clientSecretExpiresAt);
    }

    /**
     * 判断是否为公共客户端(无密钥)
     *
     * @return true-公共客户端, false-机密客户端
     */
    public boolean isPublicClient() {
        return clientSecret == null || clientSecret.isEmpty();
    }

    /**
     * 判断客户端是否被开放平台停用。
     *
     * <p>开放应用管理把启停状态写入 {@code clientSettings.openPlatform.status}；
     * 该字段只是元数据，真正的拒绝必须发生在令牌签发/授权码生成前。未声明状态的普通
     * OAuth 客户端不受影响（视为启用）。
     *
     * @return 声明为 {@code DISABLED} 时返回 true
     */
    public boolean isDisabled() {
        if (clientSettings == null || clientSettings.isBlank()) {
            return false;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(clientSettings);
            return "DISABLED".equalsIgnoreCase(node.path("openPlatform.status").asText(""));
        } catch (Exception malformed) {
            // 元数据损坏时不扩大拒绝面：仍以既有审核流程处理。
            return false;
        }
    }

    /**
     * 判断是否支持指定的授权类型
     *
     * @param grantType 授权类型
     * @return true-支持, false-不支持
     */
    public boolean supportsGrantType(String grantType) {
        return authorizationGrantTypes != null && authorizationGrantTypes.contains(grantType);
    }

    /**
     * 判断是否支持指定的作用域
     *
     * @param scope 作用域
     * @return true-支持, false-不支持
     */
    public boolean supportsScope(String scope) {
        return scopes != null && scopes.contains(scope);
    }
}
