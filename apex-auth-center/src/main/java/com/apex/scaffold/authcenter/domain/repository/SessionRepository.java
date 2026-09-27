package com.apex.scaffold.authcenter.domain.repository;

import com.apex.scaffold.authcenter.domain.model.entity.SessionEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 会话仓储接口
 *
 * <p>定义会话实体的持久化操作，遵循DDD仓储模式。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
public interface SessionRepository {

    /**
     * 保存会话
     *
     * @param session 会话实体
     * @return 保存后的会话实体
     */
    Mono<SessionEntity> save(SessionEntity session);

    /**
     * 根据ID查找会话
     *
     * @param id 会话ID
     * @return 会话实体，如果不存在返回空
     */
    Mono<SessionEntity> findById(Long id);

    /**
     * 查找所有会话
     *
     * @return 所有会话实体流
     */
    Flux<SessionEntity> findAll();

    /**
     * 根据会话标识查找会话
     *
     * @param sessionId 会话唯一标识
     * @return 会话实体，如果不存在返回空
     */
    Mono<SessionEntity> findBySessionId(String sessionId);

    /**
     * 根据Token ID查找会话
     *
     * @param tokenId Token ID
     * @return 会话实体，如果不存在返回空
     */
    Mono<SessionEntity> findByTokenId(Long tokenId);

    /**
     * 根据用户ID查找所有会话
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 会话实体流
     */
    Flux<SessionEntity> findByUserId(Long userId, String tenantId);

    /**
     * 根据用户ID查找活跃会话
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 活跃的会话实体流
     */
    Flux<SessionEntity> findActiveByUserId(Long userId, String tenantId);

    /**
     * 更新会话
     *
     * @param session 会话实体
     * @return 更新后的会话实体
     */
    Mono<SessionEntity> update(SessionEntity session);

    /**
     * 删除会话（逻辑删除）
     *
     * @param id 会话ID
     * @return 删除是否成功
     */
    Mono<Boolean> deleteById(Long id);

    /**
     * 终止用户的所有会话
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 终止是否成功
     */
    Mono<Boolean> terminateByUserId(Long userId, String tenantId);

    /**
     * 终止指定会话
     *
     * @param sessionId 会话唯一标识
     * @return 终止是否成功
     */
    Mono<Boolean> terminateBySessionId(String sessionId);

    /**
     * 更新会话最后活跃时间
     *
     * @param sessionId 会话唯一标识
     * @return 更新是否成功
     */
    Mono<Boolean> updateLastActive(String sessionId);

    /**
     * 查找已过期的会话
     *
     * @return 已过期的会话实体流
     */
    Flux<SessionEntity> findExpiredSessions();

    /**
     * 清理已过期的会话
     *
     * @return 清理的会话数量
     */
    Mono<Long> cleanupExpiredSessions();
}
