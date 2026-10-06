package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.record.AuditLogRecord;
import com.apex.scaffold.domain.repository.AuditLogQueryRepository;
import com.apex.scaffold.infrastructure.persistence.mapper.OperationLogMapper;
import com.apex.scaffold.infrastructure.persistence.po.OperationLogPO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 审计日志查询仓储实现（MyBatis-Plus）。
 *
 * <p>审计写入由 {@code OperationLogAspect} 切面 + {@code OperationLogService}
 * 落库到 {@code sys_operation_log} 表，本实现面向该表分页查询（写读同源，
 * 保证管理后台「审计日志」页面可查到实际操作记录），PO 转换为领域
 * {@link AuditLogRecord}，JDBC 阻塞调用隔离到 boundedElastic 调度器。</p>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 2.2.0
 */
@Repository
public class AuditLogQueryRepositoryImpl implements AuditLogQueryRepository {

    /** 操作日志 Mapper（{@code sys_operation_log} 表） */
    private final OperationLogMapper operationLogMapper;

    /**
     * 构造审计日志查询仓储实现。
     *
     * @param operationLogMapper 操作日志 Mapper
     */
    public AuditLogQueryRepositoryImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /** 分页查询审计日志（按主键倒序）。 */
    @Override
    public Mono<PageResponse<AuditLogRecord>> search(String module, String operatorName, int pageNum, int pageSize) {
        int boundedSize = Math.min(Math.max(pageSize, 1), 100);
        return Mono.fromCallable(() -> {
                    Page<OperationLogPO> page = new Page<>(pageNum, boundedSize);
                    operationLogMapper.selectPage(
                            page,
                            ConditionUtil.lambda(OperationLogPO.class)
                                    .likeIfPresent(OperationLogPO::getModule, module)
                                    .likeIfPresent(OperationLogPO::getUserName, operatorName)
                                    .orderByDesc(OperationLogPO::getId));
                    return PageResponse.of(
                            page.getRecords().stream().map(this::toRecord).collect(Collectors.toList()),
                            page.getTotal(),
                            pageNum,
                            boundedSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** PO → 领域记录转换（{@code sys_operation_log} 字段映射）。 */
    private AuditLogRecord toRecord(OperationLogPO po) {
        AuditLogRecord r = new AuditLogRecord();
        r.setId(po.getId());
        r.setOperatorId(po.getUserId() != null ? String.valueOf(po.getUserId()) : null);
        r.setOperatorName(po.getUserName());
        r.setOperatorIp(po.getIp());
        r.setModule(po.getModule());
        r.setOperationType(po.getOperationType());
        r.setDescription(po.getDescription());
        r.setMethod(po.getRequestMethod());
        r.setRequestUrl(po.getRequestUri());
        r.setRequestParams(po.getRequestParams());
        r.setResponseResult(po.getResponseResult());
        r.setDuration(po.getExecutionTime());
        r.setStatus(po.getStatus() == null ? null : (po.getStatus() == 0 ? "SUCCESS" : "FAILED"));
        r.setCreateTime(po.getCreateTime());
        return r;
    }
}
