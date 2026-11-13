package com.apex.scaffold.office.infrastructure.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.office.domain.approval.ApprovalCancelOperation;
import com.apex.scaffold.office.domain.approval.ApprovalCancelOperationRepository;
import com.apex.scaffold.office.domain.approval.ApprovalCancelStatus;
import com.apex.scaffold.office.infrastructure.converter.ApprovalCancelOperationConverter;
import com.apex.scaffold.office.infrastructure.mapper.ApprovalCancelOperationMapper;
import com.apex.scaffold.office.infrastructure.mapper.ApprovalProcessMapper;
import com.apex.scaffold.office.infrastructure.po.ApprovalCancelOperationPO;
import com.apex.scaffold.office.infrastructure.po.ApprovalProcessPO;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

/**
 * 审批撤销编排状态仓储实现。
 *
 * <p>负责撤销编排记录的持久化，以及「远端撤销成功后本地落库」的补偿事务。
 *
 * <p>关键实现：
 * <ul>
 *   <li>租户隔离：查询条件显式携带租户ID</li>
 *   <li>阻塞隔离：全部JDBC操作在jdbc阻塞池执行</li>
 *   <li>补偿事务：{@code completeCancel} 在同一同步事务内更新审批流程与编排记录，
 *       审批流程更新使用乐观锁，版本冲突时整体回滚并返回false，由调用方按远端已撤销状态重试</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ApprovalCancelOperationRepositoryImpl implements ApprovalCancelOperationRepository {

    private final ApprovalCancelOperationMapper operationMapper;
    private final ApprovalProcessMapper approvalProcessMapper;
    private final ApprovalCancelOperationConverter converter;
    private final PlatformTransactionManager transactionManager;
    private final BlockingIoSchedulers blockingIoSchedulers;

    @Override
    public Mono<ApprovalCancelOperation> findByTenantAndKey(Long tenantId, String idempotencyKey) {
        return Mono.fromCallable(() -> {
                    ApprovalCancelOperationPO po = operationMapper.selectByTenantAndKey(tenantId, idempotencyKey);
                    return po != null ? converter.toEntity(po) : null;
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(found -> {
                    if (found != null) {
                        log.debug("查询撤销编排记录成功，租户：{}，幂等键：{}，状态：{}", tenantId, idempotencyKey, found.getStatus());
                    }
                });
    }

    @Override
    public Mono<ApprovalCancelOperation> save(ApprovalCancelOperation operation) {
        return Mono.fromCallable(() -> {
                    ApprovalCancelOperationPO po = converter.toPO(operation);
                    operationMapper.insert(po);
                    return converter.toEntity(po);
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(saved -> log.debug("撤销编排记录已保存，ID：{}，状态：{}", saved.getId(), saved.getStatus()));
    }

    @Override
    public Mono<ApprovalCancelOperation> update(ApprovalCancelOperation operation) {
        return Mono.fromCallable(() -> {
                    ApprovalCancelOperationPO po = converter.toPO(operation);
                    operationMapper.updateById(po);
                    return converter.toEntity(operationMapper.selectById(po.getId()));
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(updated -> log.debug("撤销编排记录已更新，ID：{}，状态：{}", updated.getId(), updated.getStatus()));
    }

    @Override
    public Mono<Boolean> completeCancel(Long tenantId, String processInstanceId, Long operationId) {
        return Mono.fromCallable(() -> {
                    TransactionTemplate template = new TransactionTemplate(transactionManager);
                    Boolean completed = template.execute(status -> {
                        ApprovalProcessPO process =
                                approvalProcessMapper.selectByProcessInstanceId(processInstanceId, tenantId);
                        if (process == null) {
                            throw new IllegalStateException("审批流程不存在: " + processInstanceId);
                        }
                        process.setStatus("CANCELLED");
                        process.setCompletedAt(LocalDateTime.now());
                        // 携带原 version 触发乐观锁：并发审批推进后版本已变更，需回滚由调用方重试。
                        int processRows = approvalProcessMapper.updateById(process);
                        if (processRows == 0) {
                            return false;
                        }

                        ApprovalCancelOperationPO operation = operationMapper.selectById(operationId);
                        if (operation == null) {
                            throw new IllegalStateException("撤销编排记录不存在: " + operationId);
                        }
                        operation.setStatus(ApprovalCancelStatus.COMPLETED.name());
                        operation.setErrorMessage(null);
                        operation.setUpdateTime(LocalDateTime.now());
                        int operationRows = operationMapper.updateById(operation);
                        if (operationRows == 0) {
                            return false;
                        }
                        return true;
                    });
                    return Boolean.TRUE.equals(completed);
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(
                        result -> log.debug("撤销补偿事务完成，流程实例：{}，编排记录：{}，结果：{}", processInstanceId, operationId, result));
    }
}
