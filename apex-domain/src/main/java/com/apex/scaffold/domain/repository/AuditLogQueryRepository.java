package com.apex.scaffold.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.record.AuditLogRecord;
import reactor.core.publisher.Mono;

/**
 * 审计日志查询仓储接口。
 *
 * <p>定义操作审计日志分页查询的持久化契约，由基础设施层实现
 * （MyBatis-Plus），应用层查询服务面向本接口编程（依赖倒置 DIP）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
public interface AuditLogQueryRepository {

    /**
     * 分页查询审计日志。
     *
     * @param module       操作模块（可为空）
     * @param operatorName 操作人姓名（可为空）
     * @param pageNum      页码，从 1 开始
     * @param pageSize     每页大小，限制在 1-100
     * @return 审计日志记录分页结果
     */
    Mono<PageResponse<AuditLogRecord>> search(String module, String operatorName, int pageNum, int pageSize);
}
