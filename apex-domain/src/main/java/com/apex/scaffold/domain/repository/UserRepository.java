package com.apex.scaffold.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.entity.UserEntity;
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
     * 保存用户信息
     *
     * <p>如果用户ID为空，则执行创建操作；否则执行更新操作
     *
     * @param user 用户信息
     * @return 保存后的用户信息
     */
    Mono<UserEntity> save(UserEntity user);

    /**
     * 根据ID删除用户
     *
     * <p>执行逻辑删除操作
     *
     * @param id 用户ID
     * @return 成功返回Mono.empty()
     */
    Mono<Void> deleteById(Long id);

    /**
     * 查询所有用户。
     *
     * <p>返回所有未删除的用户实体流（Flux），适用于统计分析等需要全量数据的场景。
     * 对于分页展示场景，应使用 {@link #findPage(Integer, Integer)}。
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
     * @return 分页结果
     */
    Mono<PageResponse<UserEntity>> findPage(Integer pageNum, Integer pageSize);

    /** 后台按用户 ID、手机号或昵称分页检索用户。 */
    Mono<PageResponse<UserEntity>> searchForAdministration(
            String keyword, Integer status, Integer pageNum, Integer pageSize);
}
