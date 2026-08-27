package com.apex.scaffold.infrastructure.persistence.service;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.infrastructure.persistence.po.AuditLogPO;
import reactor.core.publisher.Mono;

/**
 * 审计日志服务接口
 *
 * <p>提供操作审计日志（{@code audit_log} 表）的写入与分页查询能力，
 * 支撑管理后台「系统监控-审计日志」页面（C2 审计落库闭环）。</p>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 */
public interface AuditLogService {

    /**
     * 保存审计日志。
     *
     * @param auditLog 审计日志对象
     */
    void saveAuditLog(AuditLogPO auditLog);

    /**
     * 分页查询审计日志。
     *
     * <p>支持按操作模块、操作人姓名模糊过滤，按主键倒序返回。
     * 页码从 1 开始，页大小限制在 1-100。</p>
     *
     * @param module       操作模块（可为空）
     * @param operatorName 操作人姓名（可为空）
     * @param pageNum      页码，从 1 开始
     * @param pageSize     每页大小，限制在 1-100
     * @return 审计日志分页结果
     * @since 2.2.0
     */
    Mono<PageResponse<AuditLogPO>> search(String module, String operatorName, int pageNum, int pageSize);
}
