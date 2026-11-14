package com.apex.scaffold.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.entity.UserEntity;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 用户仓储接口
 *
 * <p>用户领域的仓储接口，定义用户相关的持久化操作方法
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface UserRepository {
    /**
     * 根据ID查询用户
     *
     * @param id 用户ID
     * @return 用户信息，若不存在则返回Mono.empty()
     */
    Mono<UserEntity> findById(Long id);

    /**
     * 根据 ID 批量查询用户，用于候选照片权重批量计算。
     *
     * @param ids 用户 ID 列表
     * @return 用户实体列表
     */
    Flux<UserEntity> findByIds(java.util.List<Long> ids);

    /**
     * 根据微信OpenID查询用户
     *
     * @param openId 微信OpenID
     * @return 用户信息，若不存在则返回Mono.empty()
     */
    Mono<UserEntity> findByOpenId(String openId);

    /**
     * 根据手机号查询用户
     *
     * @param phone 手机号
     * @return 用户信息，若不存在则返回Mono.empty()
     */
    Mono<UserEntity> findByPhone(String phone);

    /**
     * 根据登录账号查询用户。
     *
     * <p>账号密码登录场景使用：按 {@code username}（{@code uk_username} 唯一索引）
     * 精确查询，返回用户实体（含 BCrypt 密码哈希）。用户不存在时返回
     * {@link Mono#empty()}，由认证策略统一转换为登录失败错误。</p>
     *
     * @param username 登录账号
     * @return 用户信息，若不存在则返回 {@code Mono.empty()}
     * @since 2.2.0
     */
    Mono<UserEntity> findByUsername(String username);

    /**
     * 根据 Keycloak 用户唯一标识查询用户。
     *
     * <p>Keycloak OIDC 登录场景使用：按 {@code keycloak_sub}（{@code uk_keycloak_sub} 唯一索引）
     * 精确查询，返回用户实体。用户不存在时返回 {@link Mono#empty()}。</p>
     *
     * @param keycloakSub Keycloak 用户唯一标识（sub claim）
     * @return 用户信息，若不存在则返回 {@code Mono.empty()}
     * @since 2.4.0
     */
    Mono<UserEntity> findByKeycloakSub(String keycloakSub);

    /**
     * 保存用户信息
     *
     * <p>如果用户ID为空，则执行创建操作；否则执行更新操作
     *
     * @param user 用户信息
     * @return 保存后的用户信息
     */
    Mono<UserEntity> save(UserEntity user);

    /**
     * 更新用户最后心跳时间（在线判定）。
     *
     * <p>登录成功与活跃请求时调用，将 {@code last_heartbeat_time} 刷新为当前时间；
     * 超过 {@link #ONLINE_WINDOW_MINUTES} 分钟未更新的用户视为离线。</p>
     *
     * @param userId 用户 ID
     * @since 2.2.0
     */
    Mono<Void> updateLastHeartbeat(Long userId);

    /**
     * 分页查询在线用户。
     *
     * <p>在线判定：最近 {@link #ONLINE_WINDOW_MINUTES} 分钟内有心跳更新
     * （{@code last_heartbeat_time} 在窗口内）的用户，按心跳时间倒序返回。</p>
     *
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页大小
     * @return 在线用户分页结果
     * @since 2.2.0
     */
    Mono<com.apex.scaffold.common.dto.PageResponse<UserEntity>> findOnlineUsers(int pageNum, int pageSize);

    /**
     * 在线判定窗口（分钟）。
     *
     * <p>最近 {@code ONLINE_WINDOW_MINUTES} 分钟内有心跳的用户视为在线。</p>
     *
     * @since 2.2.0
     */
    int ONLINE_WINDOW_MINUTES = 5;

    /**
     * 根据ID删除用户
     *
     * <p>执行逻辑删除操作
     *
     * @param id 用户ID
     */
    Mono<Void> deleteById(Long id);

    /**
     * 查询所有用户。
     *
     * <p>返回所有未删除的用户实体流（Flux），适用于统计分析等需要全量数据的场景。
     * 对于分页展示场景，应使用分页查询方法。
     *
     * @return 所有用户实体的 Flux 流
     * @since 1.0.0
     */
    Flux<UserEntity> findAll();

    /**
     * 分页查询用户
     *
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param orderBy 排序字段
     * @param isAsc 是否升序
     * @return 分页结果
     */
    Mono<PageResponse<UserEntity>> findPage(Integer pageNum, Integer pageSize, String orderBy, Boolean isAsc);

    /**
     * 后台按用户 ID、手机号或昵称分页检索用户。
     *
     * @param keyword 关键字（用户ID、手机号或昵称）
     * @param status 用户状态
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    Mono<PageResponse<UserEntity>> searchForAdministration(
            String keyword, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 按手机号、邮箱或登录名批量解析用户。
     *
     * <p>供组织目录内部接口使用：外部身份提供商（如企业通讯录）只持有手机号、
     * 邮箱或登录名，需要映射到本租户真实的 {@code sys_user.id}。三个条件以
     * {@code IN} 组合成一次查询，任一匹配即命中；空集合条件会被忽略。</p>
     *
     * <p>租户过滤由持久化层租户拦截器按当前租户上下文注入，调用方必须先装配租户。</p>
     *
     * @param phones    手机号集合，可为空
     * @param emails    邮箱集合，可为空
     * @param usernames 登录名集合，可为空
     * @return 命中的用户实体流，三者均为空时返回空流
     */
    Flux<UserEntity> findByPhonesOrEmailsOrUsernames(List<String> phones, List<String> emails, List<String> usernames);

    /**
     * 统计当前租户下的有效用户数。
     *
     * <p>供租户用户配额断言使用：只统计未删除用户，租户范围由持久化层租户拦截器
     * 按当前租户上下文注入，调用方必须先装配租户。</p>
     *
     * @return 当前租户未删除用户总数
     * @since 2.4.1
     */
    Mono<Long> countByCurrentTenant();
}
