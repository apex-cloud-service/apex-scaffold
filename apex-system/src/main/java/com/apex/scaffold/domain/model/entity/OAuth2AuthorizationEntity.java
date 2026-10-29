package com.apex.scaffold.domain.model.entity;

import java.time.Instant;
import lombok.Data;

/**
 * OAuth2授权记录实体
 *
 * <p>存储完整的OAuth2授权流程信息，包括授权码、访问令牌、刷新令牌等。
 * 此实体用于审计和追踪OAuth2授权的完整生命周期。
 *
 * <h2>设计说明</h2>
 * <p>对应数据库表: {@code oauth2_authorization}
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>审计OAuth2授权历史</li>
 *   <li>追踪Token签发记录</li>
 *   <li>安全事件调查</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Data
public class OAuth2AuthorizationEntity {

    /**
     * 唯一标识符
     */
    private String id;

    /**
     * 客户端注册ID
     *
     * <p>关联到 {@code oauth2_registered_client.id}
     */
    private String registeredClientId;

    /**
     * 用户主体名称 (OpenID)
     */
    private String principalName;

    /**
     * 授权类型
     *
     * <p>可选值:
     * <ul>
     *   <li>authorization_code - 授权码模式</li>
     *   <li>client_credentials - 客户端凭证模式</li>
     *   <li>password - 密码模式</li>
     *   <li>refresh_token - 刷新令牌模式</li>
     * </ul>
     */
    private String authorizationGrantType;

    /**
     * 已授权的scope列表 (逗号分隔)
     */
    private String authorizedScopes;

    /**
     * OAuth2 state参数
     *
     * <p>用于防止CSRF攻击
     */
    private String state;

    /**
     * 授权码值
     */
    private String authorizationCodeValue;

    /**
     * 授权码签发时间
     */
    private Instant authorizationCodeIssuedAt;

    /**
     * 授权码过期时间
     */
    private Instant authorizationCodeExpiresAt;

    /**
     * 访问令牌值
     */
    private String accessTokenValue;

    /**
     * 访问令牌签发时间
     */
    private Instant accessTokenIssuedAt;

    /**
     * 访问令牌过期时间
     */
    private Instant accessTokenExpiresAt;

    /**
     * 访问令牌类型
     *
     * <p>通常为 "Bearer"
     */
    private String accessTokenType;

    /**
     * 访问令牌的scope (逗号分隔)
     */
    private String accessTokenScopes;

    /**
     * 刷新令牌值
     */
    private String refreshTokenValue;

    /**
     * 刷新令牌签发时间
     */
    private Instant refreshTokenIssuedAt;

    /**
     * 刷新令牌过期时间
     */
    private Instant refreshTokenExpiresAt;
}
