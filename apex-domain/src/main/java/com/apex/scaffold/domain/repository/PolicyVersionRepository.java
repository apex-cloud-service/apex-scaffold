package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.PolicyVersionEntity;
import reactor.core.publisher.Mono;

/**
 * 政策版本仓储接口（领域层）
 *
 * <p>
 * 定义协议/政策版本（PolicyVersion）的持久化操作规范， 由基础设施层的 {@code PolicyVersionRepositoryImpl} 提供具体实现。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface PolicyVersionRepository {
    /**
     * 按政策类型查询最新版本。
     *
     * <p>
     * 根据政策类型（如隐私政策、用户协议等）查询最新的版本记录， 用于向客户端展示或判断用户是否需要重新同意。
     *
     * @param policyType 政策类型标识
     * @return 政策版本实体的 Mono 包装，可能为空
     */
    Mono<PolicyVersionEntity> findLatestByType(String policyType);
}
