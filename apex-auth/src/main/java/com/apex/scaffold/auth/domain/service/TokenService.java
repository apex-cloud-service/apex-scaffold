package com.apex.scaffold.auth.domain.service;

import com.apex.scaffold.auth.domain.model.entity.TokenEntity;
import com.apex.scaffold.auth.domain.model.vo.TokenPair;
import reactor.core.publisher.Mono;

/**
 * Token服务接口
 *
 * <p>定义Token的全生命周期管理接口，包括签发、验证、刷新、撤销等核心功能。
 * 支持JWT和Opaque Token两种格式。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>Token签发：生成访问令牌和刷新令牌</li>
 *   <li>Token验证：验证签名、过期时间、黑名单</li>
 *   <li>Token刷新：使用刷新令牌获取新的访问令牌</li>
 *   <li>Token撤销：主动撤销Token，加入黑名单</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * TokenService tokenService = ...;
 * TokenPair tokens = tokenService.issueToken(userId, tenantId, deviceId).block();
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2026-09-20
 */
public interface TokenService {

    /**
     * 签发Token对
     *
     * <p>为用户签发访问令牌和刷新令牌。
     * 访问令牌用于API访问，默认有效期2小时。
     * 刷新令牌用于获取新的访问令牌，默认有效期7天。
     * 未指定客户端类型时按旧签名处理（claims 不含 clientType）。
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param deviceId 设备ID
     * @param clientIp 客户端IP
     * @return Token对的Mono
     */
    Mono<TokenPair> issueToken(Long userId, String tenantId, String deviceId, String clientIp);

    /**
     * 签发Token对（多端统一账户模型，携带客户端类型）
     *
     * <p>在 claims 中额外注入 {@code clientType}（客户端类型编码）；
     * {@code accountId}（统一账户ID）与 {@code username} 由用户档案自动携带，
     * 供主应用装配统一身份。会话身份由 {@code jti}/{@code tokenFamily} 承担
     * （令牌全量落库、按 jti 撤销），不再单独注入 sid。
     *
     * @param userId     用户ID
     * @param tenantId   租户ID
     * @param deviceId   设备ID
     * @param clientIp   客户端IP
     * @param clientType 客户端类型编码（{@code ClientType}），可为空
     * @return Token对的Mono
     * @since 3.3.0
     */
    default Mono<TokenPair> issueToken(
            Long userId, String tenantId, String deviceId, String clientIp, String clientType) {
        return issueToken(userId, tenantId, deviceId, clientIp);
    }

    /**
     * 验证Token有效性
     *
     * <p>验证Token的签名、过期时间、撤销状态和黑名单。
     *
     * @param token Token字符串
     * @return Token实体的Mono，如果Token无效则返回empty
     */
    Mono<TokenEntity> validateToken(String token);

    /**
     * 刷新Token
     *
     * <p>使用刷新令牌获取新的访问令牌。
     * 刷新令牌本身不会被刷新，仍使用原有的刷新令牌。
     *
     * @param refreshToken 刷新令牌
     * @return 新的Token对的Mono
     */
    Mono<TokenPair> refreshToken(String refreshToken);

    /**
     * 撤销Token
     *
     * <p>主动撤销Token，将Token加入黑名单。
     * 撤销后的Token无法再使用。
     *
     * @param token Token字符串
     * @return 撤销结果的Mono
     */
    Mono<Void> revokeToken(String token);

    /**
     * 撤销用户的所有Token
     *
     * <p>撤销指定用户的所有访问令牌和刷新令牌。
     * 用于强制用户下线或安全事件响应。
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 撤销结果的Mono
     */
    Mono<Void> revokeAllUserTokens(Long userId, String tenantId);

    /**
     * 从Token中提取用户ID
     *
     * @param token Token字符串
     * @return 用户ID的Mono
     */
    Mono<Long> extractUserId(String token);
}
