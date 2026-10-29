package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.SysSsoSessionEntity;
import java.time.LocalDateTime;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SSO 会话仓储接口
 *
 * <p>定义单点登录（SSO）会话的持久化操作契约，管理用户的 SSO 会话生命周期。
 * 支持会话的增删改查、过期会话清理、按用户/租户查询会话等功能。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>SSO 会话的基础 CRUD 操作</li>
 *   <li>按会话索引查询（sessionIndex 是 SAML/OAuth2 会话唯一标识）</li>
 *   <li>按用户查询活动会话（用户登出时需撤销所有会话）</li>
 *   <li>按租户查询会话（租户管理员监控登录情况）</li>
 *   <li>查询和清理过期会话（定时任务）</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>单点登录：创建新会话并关联 SSO 令牌</li>
 *   <li>单点登出：通过 sessionIndex 撤销特定会话</li>
 *   <li>强制登出：查询用户的所有会话并批量撤销</li>
 *   <li>会话管理：查看和管理租户下的活动会话</li>
 *   <li>会话清理：定期清理过期会话释放存储</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>响应式编程：所有方法返回 Mono/Flux</li>
 *   <li>会话索引：sessionIndex 作为 SSO 会话的业务唯一标识</li>
 *   <li>过期管理：支持基于时间的会话过期查询</li>
 *   <li>租户隔离：支持多租户环境的会话隔离</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SysSsoSessionEntity
 */
public interface SysSsoSessionRepository {

    /**
     * 根据ID查询SSO会话
     *
     * @param id 会话ID
     * @return 会话实体
     */
    Mono<SysSsoSessionEntity> findById(Long id);

    /**
     * 根据会话索引查询SSO会话
     *
     * @param sessionIndex 会话索引
     * @return 会话实体
     */
    Mono<SysSsoSessionEntity> findBySessionIndex(String sessionIndex);

    /**
     * 根据用户ID查询会话列表
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    Flux<SysSsoSessionEntity> findByUserId(Long userId);

    /**
     * 根据租户ID查询会话列表
     *
     * @param tenantId 租户ID
     * @return 会话列表
     */
    Flux<SysSsoSessionEntity> findByTenantId(String tenantId);

    /**
     * 查询过期会话列表
     *
     * @param before 过期时间点
     * @return 过期会话列表
     */
    Flux<SysSsoSessionEntity> findExpiredSessions(LocalDateTime before);

    /**
     * 插入SSO会话
     *
     * @param entity 会话实体
     * @return 影响行数
     */
    Mono<Integer> insert(SysSsoSessionEntity entity);

    /**
     * 根据ID更新SSO会话
     *
     * @param entity 会话实体
     * @return 影响行数
     */
    Mono<Integer> updateById(SysSsoSessionEntity entity);

    /**
     * 根据ID删除SSO会话
     *
     * @param id 会话ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
