package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.BlacklistEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 黑名单仓储接口（领域层）。
 *
 * <p>用于支撑：
 *
 * <ul>
 *   <li>拉黑/取消拉黑（upsert）
 *   <li>双向屏蔽校验（isBlocked）
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface BlacklistRepository {
    /**
     * 新增或更新黑名单关系。
     *
     * @param entity 黑名单实体
     * @return 保存后的实体
     */
    Mono<BlacklistEntity> upsert(BlacklistEntity entity);

    /**
     * 列出当前用户已启用的黑名单目标用户 ID 列表。
     *
     * @param userId 用户 ID
     * @return 目标用户 ID 列表
     */
    Mono<List<Long>> listEnabledTargetUserIds(Long userId);

    /**
     * 判断 userId 是否拉黑了 targetUserId（enabled=1）。
     *
     * @param userId 用户 ID
     * @param targetUserId 目标用户 ID
     * @return true 表示已拉黑
     */
    Mono<Boolean> isBlocked(Long userId, Long targetUserId);
}
