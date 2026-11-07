package com.apex.scaffold.auth.domain.repository;

import com.apex.scaffold.auth.domain.model.OAuth2Authorization;
import java.time.LocalDateTime;
import reactor.core.publisher.Mono;

/**
 * OAuth2 授权记录持久化端口。
 *
 * <p>凭据以 SHA-256 哈希参与匹配，明文令牌不进入本端口。查询类方法
 * 在租户内未命中时返回空 {@link Mono}。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since  1.3.1
 */
public interface OAuth2AuthorizationPort {

    /**
     * 保存授权码签发记录。
     *
     * @param tenant  租户
     * @param command 授权码记录
     * @return 写入完成后发出完成信号
     */
    Mono<Void> saveAuthorizationCode(String tenant, OAuth2AuthorizationCodeCommand command);

    /**
     * 保存令牌签发的授权记录（客户端凭证、密码、刷新令牌流程）。
     *
     * @param tenant  租户
     * @param command 令牌记录
     * @return 写入完成后发出完成信号
     */
    Mono<Void> saveTokenGrant(String tenant, OAuth2TokenGrantCommand command);

    /**
     * 按授权码哈希查询授权记录。
     *
     * @param tenant 租户
     * @param codeHash 授权码哈希
     * @return 授权记录；未命中时为空 Mono
     */
    Mono<OAuth2Authorization> findByAuthorizationCode(String tenant, String codeHash);

    /**
     * 按访问令牌哈希查询授权记录。
     *
     * @param tenant 租户
     * @param accessTokenHash 访问令牌哈希
     * @return 授权记录；未命中时为空 Mono
     */
    Mono<OAuth2Authorization> findByAccessToken(String tenant, String accessTokenHash);

    /**
     * 按刷新令牌哈希查询授权记录。
     *
     * @param tenant 租户
     * @param refreshTokenHash 刷新令牌哈希
     * @return 授权记录；未命中时为空 Mono
     */
    Mono<OAuth2Authorization> findByRefreshToken(String tenant, String refreshTokenHash);

    /**
     * 用授权码一次性兑换访问/刷新令牌，兑换成功后授权码置空。
     *
     * @param tenant  租户
     * @param command 兑换参数
     * @return 命中且未过期、未撤销，兑换成功时为 {@code true}
     */
    Mono<Boolean> exchangeCode(String tenant, OAuth2CodeExchangeCommand command);

    /**
     * 轮换刷新令牌并同步更新访问令牌与授权范围。
     *
     * @param tenant  租户
     * @param command 轮换参数
     * @return 旧刷新令牌有效且轮换成功时为 {@code true}
     */
    Mono<Boolean> rotateRefresh(String tenant, OAuth2RefreshRotationCommand command);

    /**
     * 按令牌哈希撤销指定客户端的授权记录。
     *
     * @param tenant 租户
     * @param tokenHash 访问或刷新令牌哈希
     * @param clientId 客户端标识
     * @return 撤销的记录数
     */
    Mono<Integer> revokeByTokenHash(String tenant, String tokenHash, String clientId);

    /**
     * 授权码签发记录。
     *
     * @param id              授权记录 ID
     * @param clientId        客户端标识
     * @param principalName   资源所有者标识
     * @param authorizedScopes 授权范围
     * @param redirectUri     重定向地址
     * @param codeChallenge   PKCE 码挑战
     * @param codeChallengeMethod PKCE 挑战方法
     * @param authorizationCodeHash 授权码哈希
     * @param authorizationCodeExpiresAt 授权码过期时间（UTC）
     */
    record OAuth2AuthorizationCodeCommand(
            String id,
            String clientId,
            String principalName,
            String authorizedScopes,
            String redirectUri,
            String codeChallenge,
            String codeChallengeMethod,
            String authorizationCodeHash,
            LocalDateTime authorizationCodeExpiresAt) {}

    /**
     * 令牌签发的授权记录。
     *
     * @param id              授权记录 ID
     * @param clientId        客户端标识
     * @param principalName   资源所有者标识
     * @param authorizationGrantType 授权类型
     * @param authorizedScopes 授权范围
     * @param accessTokenHash 访问令牌哈希
     * @param accessTokenExpiresAt 访问令牌过期时间（UTC）
     * @param refreshTokenHash 刷新令牌哈希，无刷新令牌时为 {@code null}
     * @param refreshTokenExpiresAt 刷新令牌过期时间（UTC），无刷新令牌时为 {@code null}
     */
    record OAuth2TokenGrantCommand(
            String id,
            String clientId,
            String principalName,
            String authorizationGrantType,
            String authorizedScopes,
            String accessTokenHash,
            LocalDateTime accessTokenExpiresAt,
            String refreshTokenHash,
            LocalDateTime refreshTokenExpiresAt) {}

    /**
     * 授权码兑换参数。
     *
     * @param authorizationCodeHash 授权码哈希
     * @param clientId              客户端标识
     * @param redirectUri           重定向地址
     * @param accessTokenHash       访问令牌哈希
     * @param accessTokenExpiresAt  访问令牌过期时间（UTC）
     * @param refreshTokenHash      刷新令牌哈希
     * @param refreshTokenExpiresAt 刷新令牌过期时间（UTC）
     */
    record OAuth2CodeExchangeCommand(
            String authorizationCodeHash,
            String clientId,
            String redirectUri,
            String accessTokenHash,
            LocalDateTime accessTokenExpiresAt,
            String refreshTokenHash,
            LocalDateTime refreshTokenExpiresAt) {}

    /**
     * 刷新令牌轮换参数。
     *
     * @param clientId             客户端标识
     * @param oldRefreshTokenHash  旧刷新令牌哈希
     * @param newRefreshTokenHash  新刷新令牌哈希
     * @param refreshTokenExpiresAt 新刷新令牌过期时间（UTC）
     * @param accessTokenHash      新访问令牌哈希
     * @param accessTokenExpiresAt 新访问令牌过期时间（UTC）
     * @param authorizedScopes     轮换后的授权范围
     */
    record OAuth2RefreshRotationCommand(
            String clientId,
            String oldRefreshTokenHash,
            String newRefreshTokenHash,
            LocalDateTime refreshTokenExpiresAt,
            String accessTokenHash,
            LocalDateTime accessTokenExpiresAt,
            String authorizedScopes) {}
}
