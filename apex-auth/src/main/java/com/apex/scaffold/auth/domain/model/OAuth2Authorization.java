package com.apex.scaffold.auth.domain.model;

import java.time.LocalDateTime;

/**
 * OAuth2 授权记录（RFC 6749 授权码流程与令牌状态的领域视图）。
 *
 * <p>一条记录同时承载授权码、访问令牌与刷新令牌三种凭据的生命周期；
 * 凭据本身只以 SHA-256 十六进制哈希落库，明文不进入领域模型。
 *
 * @param clientId                客户端标识
 * @param principalName           资源所有者标识
 * @param authorizationGrantType  签发时使用的授权类型
 * @param authorizedScopes        授权范围（空格分隔）
 * @param redirectUri             授权码绑定重定向地址
 * @param codeChallenge           PKCE 码挑战
 * @param codeChallengeMethod     PKCE 挑战方法
 * @param authorizationCodeHash   授权码哈希
 * @param authorizationCodeExpiresAt 授权码过期时间（UTC）
 * @param accessTokenHash         访问令牌哈希
 * @param accessTokenExpiresAt    访问令牌过期时间（UTC）
 * @param refreshTokenHash        刷新令牌哈希
 * @param refreshTokenExpiresAt   刷新令牌过期时间（UTC）
 * @param revoked                 是否已撤销
 * @author Apex Team
 * @version 1.3.1
 * @since  1.3.1
 */
public record OAuth2Authorization(
        String clientId,
        String principalName,
        String authorizationGrantType,
        String authorizedScopes,
        String redirectUri,
        String codeChallenge,
        String codeChallengeMethod,
        String authorizationCodeHash,
        LocalDateTime authorizationCodeExpiresAt,
        String accessTokenHash,
        LocalDateTime accessTokenExpiresAt,
        String refreshTokenHash,
        LocalDateTime refreshTokenExpiresAt,
        boolean revoked) {

    /**
     * 令牌在给定时刻是否有效：未撤销、过期时间存在且晚于 {@code now}。
     *
     * @param expiry 该凭据的过期时间
     * @param now    比较基准时刻（UTC）
     * @return 可作为有效凭据使用时为 {@code true}
     */
    public boolean effectiveAt(LocalDateTime expiry, LocalDateTime now) {
        return !revoked && expiry != null && expiry.isAfter(now);
    }
}
