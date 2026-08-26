package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.SystemMessageEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 系统消息仓储接口
 *
 * <p>定义系统消息相关的持久化操作方法
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface SystemMessageRepository {
    /**
     * 保存系统消息
     *
     * @param entity 系统消息实体
     * @return 保存后的系统消息实体
     */
    Mono<SystemMessageEntity> save(SystemMessageEntity entity);

    /**
     * 根据用户ID查询系统消息列表
     *
     * @param userId 用户ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 系统消息列表
     */
    Mono<List<SystemMessageEntity>> listByUserId(Long userId, int offset, int limit);

    /**
     * 统计用户系统消息总数。
     *
     * <p>按用户ID统计该用户的系统消息总数量。
     *
     * @param userId 用户ID
     * @return 系统消息总数的 Mono 包装
     */
    Mono<Long> countByUserId(Long userId);
}
