package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.admin.AuditLogDTO;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.record.AuditLogRecord;
import com.apex.scaffold.domain.repository.AuditLogQueryRepository;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 审计日志查询服务。
 *
 * <p>面向管理后台「系统监控-审计日志」页面提供分页查询，依赖领域层
 * {@link AuditLogQueryRepository}（基础设施层实现），将领域记录转换为
 * 应用层 {@link AuditLogDTO}，保持应用层零基础设施依赖（依赖倒置 DIP）。
 * 审计写入由 {@code OperationLogService} 在业务方法执行后自动完成。</p>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 2.2.0
 * @see AuditLogQueryRepository
 */
@Service
public class AuditLogQueryService {

    /** 审计日志查询仓储（领域接口） */
    private final AuditLogQueryRepository auditLogQueryRepository;

    /**
     * 构造审计日志查询服务。
     *
     * @param auditLogQueryRepository 审计日志查询仓储
     */
    public AuditLogQueryService(AuditLogQueryRepository auditLogQueryRepository) {
        this.auditLogQueryRepository = auditLogQueryRepository;
    }

    /**
     * 分页查询审计日志。
     *
     * <p>支持按操作模块、操作人姓名模糊过滤，结果按主键倒序（最新优先），
     * 并转换为应用层 {@link AuditLogDTO}。</p>
     *
     * @param module       操作模块（可为空）
     * @param operatorName 操作人姓名（可为空）
     * @param pageNum      页码，从 1 开始
     * @param pageSize     每页大小，限制在 1-100
     * @return 审计日志 DTO 分页结果
     */
    public Mono<PageResponse<AuditLogDTO>> search(String module, String operatorName, int pageNum, int pageSize) {
        return auditLogQueryRepository.search(module, operatorName, pageNum, pageSize).map(page -> {
            var dtos = page.getList().stream().map(this::toDto).collect(Collectors.toList());
            return PageResponse.of(dtos, page.getTotal(), page.getPageNum(), page.getPageSize());
        });
    }

    /**
     * 将领域记录转换为应用层 DTO。
     *
     * @param record 审计日志领域记录
     * @return 审计日志 DTO
     */
    private AuditLogDTO toDto(AuditLogRecord record) {
        return new AuditLogDTO(
                record.getId(),
                record.getOperatorId(),
                record.getOperatorName(),
                record.getOperatorIp(),
                record.getModule(),
                record.getOperationType(),
                record.getDescription(),
                record.getMethod(),
                record.getRequestUrl(),
                record.getRequestParams(),
                record.getResponseResult(),
                record.getDuration(),
                record.getStatus(),
                record.getCreateTime());
    }
}
