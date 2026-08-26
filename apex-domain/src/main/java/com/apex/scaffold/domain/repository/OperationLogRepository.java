package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.OperationLogEntity;
import reactor.core.publisher.Mono;

/**
 * 操作日志仓储接口
 *
 * <p>仓储接口（领域层）
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface OperationLogRepository {
    /**
     * 插入操作日志
     *
     * @param log 操作日志信息
     * @return 影响行数
     */
    Mono<Integer> insert(OperationLogEntity log);
}
