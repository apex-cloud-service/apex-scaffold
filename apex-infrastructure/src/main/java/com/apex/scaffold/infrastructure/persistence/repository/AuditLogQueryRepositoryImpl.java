package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.record.AuditLogRecord;
import com.apex.scaffold.domain.repository.AuditLogQueryRepository;
import com.apex.scaffold.infrastructure.persistence.mapper.AuditLogMapper;
import com.apex.scaffold.infrastructure.persistence.po.AuditLogPO;
import com.apex.scaffold.infrastructure.persistence.util.ConditionUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 审计日志查询仓储实现（MyBatis-Plus）。
 *
 * <p>实现 {@link AuditLogQueryRepository} 契约：分页查询 {@code audit_log} 表，
 * PO 转换为领域 {@link AuditLogRecord}，JDBC 阻塞调用隔离到 boundedElastic 调度器。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@Repository
public class AuditLogQueryRepositoryImpl implements AuditLogQueryRepository {

    /** 审计日志 Mapper */
    private final AuditLogMapper auditLogMapper;

    /**
     * 构造审计日志查询仓储实现。
     *
     * @param auditLogMapper 审计日志 Mapper
     */
    public AuditLogQueryRepositoryImpl(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /** 分页查询审计日志（按主键倒序）。 */
    @Override
    public Mono<PageResponse<AuditLogRecord>> search(String module, String operatorName, int pageNum, int pageSize) {
        int boundedSize = Math.min(Math.max(pageSize, 1), 100);
        return Mono.fromCallable(() -> {
                    Page<AuditLogPO> page = new Page<>(pageNum, boundedSize);
                    auditLogMapper.selectPage(
                            page,
                            ConditionUtil.lambda(AuditLogPO.class)
                                    .likeIfPresent(AuditLogPO::getModule, module)
                                    .likeIfPresent(AuditLogPO::getOperatorName, operatorName)
                                    .orderByDesc(AuditLogPO::getId));
                    return PageResponse.of(
                            page.getRecords().stream().map(this::toRecord).collect(Collectors.toList()),
                            page.getTotal(),
                            pageNum,
                            boundedSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** PO → 领域记录转换。 */
    private AuditLogRecord toRecord(AuditLogPO po) {
        AuditLogRecord r = new AuditLogRecord();
        r.setId(po.getId());
        r.setOperatorId(po.getOperatorId());
        r.setOperatorName(po.getOperatorName());
        r.setOperatorIp(po.getOperatorIp());
        r.setModule(po.getModule());
        r.setOperationType(po.getOperationType());
        r.setDescription(po.getDescription());
        r.setMethod(po.getMethod());
        r.setRequestUrl(po.getRequestUrl());
        r.setRequestParams(po.getRequestParams());
        r.setResponseResult(po.getResponseResult());
        r.setDuration(po.getDuration());
        r.setStatus(po.getStatus());
        r.setCreateTime(po.getCreateTime());
        return r;
    }
}
