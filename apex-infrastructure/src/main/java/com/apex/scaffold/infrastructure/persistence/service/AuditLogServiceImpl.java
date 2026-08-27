package com.apex.scaffold.infrastructure.persistence.service;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.infrastructure.persistence.mapper.AuditLogMapper;
import com.apex.scaffold.infrastructure.persistence.po.AuditLogPO;
import com.apex.scaffold.infrastructure.persistence.util.ConditionUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 审计日志服务实现
 *
 * <p>基于 MyBatis-Plus 提供审计日志写入与分页查询；写入失败不阻塞主流程
 * （审计保底），分页查询在 {@link BlockingIoSchedulers#jdbc()} 调度器上
 * 执行以隔离阻塞 JDBC 调用。</p>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    /** 审计日志 Mapper */
    private final AuditLogMapper auditLogMapper;

    /**
     * 保存审计日志。
     *
     * <p>审计日志保存失败仅记录错误日志，不影响主业务流程。</p>
     *
     * @param auditLog 审计日志对象
     */
    @Override
    public void saveAuditLog(AuditLogPO auditLog) {
        try {
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.error("保存审计日志失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 分页查询审计日志。
     *
     * <p>支持按模块/操作人模糊过滤，按主键倒序（最新优先），
     * 页大小限制 1-100，JDBC 阻塞调用隔离到 boundedElastic 调度器。</p>
     *
     * @param module       操作模块（可为空）
     * @param operatorName 操作人姓名（可为空）
     * @param pageNum      页码，从 1 开始
     * @param pageSize     每页大小，限制在 1-100
     * @return 审计日志分页结果
     * @since 2.2.0
     */
    @Override
    public Mono<PageResponse<AuditLogPO>> search(String module, String operatorName, int pageNum, int pageSize) {
        int boundedSize = Math.min(Math.max(pageSize, 1), 100);
        return Mono.fromCallable(() -> {
                    Page<AuditLogPO> page = new Page<>(pageNum, boundedSize);
                    auditLogMapper.selectPage(
                            page,
                            ConditionUtil.lambda(AuditLogPO.class)
                                    .likeIfPresent(AuditLogPO::getModule, module)
                                    .likeIfPresent(AuditLogPO::getOperatorName, operatorName)
                                    .orderByDesc(AuditLogPO::getId));
                    List<AuditLogPO> records = page.getRecords();
                    return PageResponse.of(records, page.getTotal(), pageNum, boundedSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
