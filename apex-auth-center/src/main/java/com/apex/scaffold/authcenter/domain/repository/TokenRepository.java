package com.apex.scaffold.authcenter.domain.repository;

import com.apex.scaffold.authcenter.domain.model.entity.TokenEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Token存储库接口
 * 提供Token实体的响应式数据访问操作
 *
 * @author Apex Scaffold
 * @version 1.0
 * @since 1.0
 */
public interface TokenRepository {

    /**
     * 保存Token实体
     *
     * @param token Token实体
     * @return 保存后的Token实体
     */
    Mono<TokenEntity> save(TokenEntity token);

    /**
     * 根据ID查找Token
     *
     * @param id Token ID
     * @return Token实体，如果不存在则返回空Mono
     */
    Mono<TokenEntity> findById(Long id);

    /**
     * 根据访问令牌查找Token
     *
     * @param token 访问令牌字符串
     * @return Token实体，如果不存在则返回空Mono
     */
    Mono<TokenEntity> findByToken(String token);

    /**
     * 根据JTI (JWT ID)查找Token
     *
     * @param jti JWT唯一标识符
     * @return Token实体，如果不存在则返回空Mono
     */
    Mono<TokenEntity> findByJti(String jti);

    /**
     * 根据刷新令牌查找Token
     *
     * @param refreshToken 刷新令牌字符串
     * @return Token实体，如果不存在则返回空Mono
     */
    Mono<TokenEntity> findByRefreshToken(String refreshToken);

    /**
     * 根据用户ID和租户ID查找所有Token
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return Token实体流
     */
    Flux<TokenEntity> findByUserId(Long userId, String tenantId);

    /**
     * 根据用户ID和租户ID查找所有活跃的Token
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 活跃的Token实体流
     */
    Flux<TokenEntity> findActiveByUserId(Long userId, String tenantId);

    /**
     * 更新Token实体
     *
     * @param token 要更新的Token实体
     * @return 更新后的Token实体
     */
    Mono<TokenEntity> update(TokenEntity token);

    /**
     * 根据ID删除Token
     *
     * @param id Token ID
     * @return 删除成功返回true，否则返回false
     */
    Mono<Boolean> deleteById(Long id);

    /**
     * 撤销指定用户和租户的所有Token
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 撤销成功返回true，否则返回false
     */
    Mono<Boolean> revokeByUserId(Long userId, String tenantId);

    /**
     * 根据令牌字符串撤销Token
     *
     * @param token 令牌字符串
     * @return 撤销成功返回true，否则返回false
     */
    Mono<Boolean> revokeByToken(String token);

    /**
     * 根据JTI撤销Token
     *
     * @param jti JWT唯一标识符
     * @return 撤销成功返回true，否则返回false
     */
    Mono<Boolean> revokeByJti(String jti);

    /**
     * 根据JTI撤销Token（带原因和操作人）
     *
     * @param jti JWT唯一标识符
     * @param revokedBy 撤销操作人
     * @param reason 撤销原因
     * @return 撤销成功返回true，否则返回false
     */
    Mono<Boolean> revokeByJti(String jti, String revokedBy, String reason);

    /**
     * 撤销指定用户的所有Token（带原因和操作人）
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param revokedBy 撤销操作人
     * @param reason 撤销原因
     * @return 撤销的Token数量
     */
    Mono<Integer> revokeByUserId(Long userId, String tenantId, String revokedBy, String reason);

    /**
     * 根据Token家族ID撤销所有Token
     *
     * <p>用于Refresh Token轮换安全机制。
     * 当检测到Token Family中的某个Token被重复使用时，
     * 撤销整个Family中的所有Token以防止Token窃取攻击。
     *
     * @param tokenFamily Token家族ID
     * @param revokedBy 撤销操作人
     * @param reason 撤销原因
     * @return 撤销的Token数量
     */
    Mono<Integer> revokeByTokenFamily(String tokenFamily, String revokedBy, String reason);

    /**
     * 检查令牌是否存在
     *
     * @param token 令牌字符串
     * @return 存在返回true，否则返回false
     */
    Mono<Boolean> existsByToken(String token);

    /**
     * 查找所有已过期的Token
     *
     * @return 已过期的Token实体流
     */
    Flux<TokenEntity> findExpiredTokens();

    /**
     * 删除所有已过期的Token
     *
     * @return 删除的Token数量
     */
    Mono<Long> deleteExpiredTokens();
}
