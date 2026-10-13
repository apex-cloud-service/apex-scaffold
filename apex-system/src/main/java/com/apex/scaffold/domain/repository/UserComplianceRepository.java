package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.UserComplianceEntity;
import reactor.core.publisher.Mono;

/**
 * 用户合规记录仓储接口（领域层）
 *
 * <p>定义用户在合规审核/青少年模式等方面的记录持久化操作规范，
 * 由基础设施层的 {@code UserComplianceRepositoryImpl} 提供具体实现。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface UserComplianceRepository {
    /**
     * 按用户ID查询合规记录。
     *
     * <p>根据用户唯一标识查询该用户的合规状态信息，若不存在则返回空信号。
     *
     * @param userId 用户ID
     * @return 用户合规记录实体的 Mono 包装，可能为空
     */
    Mono<UserComplianceEntity> findByUserId(Long userId);

    /**
     * 创建或更新用户合规记录。
     *
     * <p>若实体未分配 id 则执行插入，否则按 id 更新已有记录，
     * 操作完成后返回数据库中最新状态的实体。
     *
     * @param entity 用户合规记录实体
     * @return 保存后最新状态的用户合规记录实体
     */
    Mono<UserComplianceEntity> save(UserComplianceEntity entity);
}
