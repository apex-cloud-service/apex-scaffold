package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.UserConsentEntity;
import reactor.core.publisher.Mono;

/**
 * 用户同意记录仓储接口（领域层）
 *
 * <p>定义用户协议/隐私政策同意记录的持久化操作规范，
 * 由基础设施层的 {@code UserConsentRepositoryImpl} 提供具体实现。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface UserConsentRepository {
    /**
     * 创建或更新用户同意记录。
     *
     * <p>若实体未分配 id 则执行插入，否则按 id 更新已有记录，
     * 操作完成后返回数据库中最新状态的实体。
     *
     * @param entity 用户同意记录实体
     * @return 保存后最新状态的用户同意记录实体
     */
    Mono<UserConsentEntity> save(UserConsentEntity entity);
}
