package com.apex.scaffold.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
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

    /**
     * 分页查询用户站内信列表。
     *
     * <p>按创建时间倒序分页返回指定用户的消息，支持仅返回未读消息的过滤。
     * 分页参数从 1 开始计数，返回结果包含总记录数、总页数与是否存在下一页。
     *
     * @param userId     用户ID
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @param unreadOnly 是否仅返回未读消息
     * @return 分页结果（含消息列表与分页元信息）
     */
    Mono<PageResponse<SystemMessageEntity>> pageByUser(Long userId, int page, int size, boolean unreadOnly);

    /**
     * 统计用户未读站内信数量。
     *
     * @param userId 用户ID
     * @return 未读消息数量的 Mono 包装
     */
    Mono<Long> countUnread(Long userId);

    /**
     * 将指定用户的单条消息标记为已读（幂等）。
     *
     * <p>仅当消息属于该用户且尚未标记已读时才执行更新。已读时返回 {@code false}，
     * 使重复调用幂等返回成功而不产生额外副作用。
     *
     * @param userId    用户ID
     * @param messageId 消息ID
     * @return 本次是否实际更新了记录（{@code true} 表示由未读转为已读）
     */
    Mono<Boolean> markRead(Long userId, Long messageId);

    /**
     * 将指定用户的全部未读消息标记为已读。
     *
     * @param userId 用户ID
     * @return 本次实际标记已读的消息数量
     */
    Mono<Long> markAllRead(Long userId);
}
