package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.UserProfileEntity;
import reactor.core.publisher.Mono;

/**
 * 用户档案仓储接口
 *
 * <p>定义用户档案相关的持久化操作方法
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface UserProfileRepository {
    /**
     * 根据用户ID查询用户档案
     *
     * @param userId 用户ID
     * @return 用户档案实体
     */
    Mono<UserProfileEntity> findByUserId(Long userId);

    /**
     * 保存用户档案
     *
     * @param entity 用户档案实体
     * @return 保存后的用户档案实体
     */
    Mono<UserProfileEntity> save(UserProfileEntity entity);
}
