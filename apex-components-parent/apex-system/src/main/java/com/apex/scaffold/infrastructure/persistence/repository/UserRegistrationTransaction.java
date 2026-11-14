package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.OperationLogEntity;
import com.apex.scaffold.domain.model.entity.SystemMessageEntity;
import com.apex.scaffold.infrastructure.persistence.converter.OperationLogPoConverter;
import com.apex.scaffold.infrastructure.persistence.converter.SystemMessagePoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.OperationLogMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.SystemMessageMapper;
import com.apex.scaffold.infrastructure.persistence.po.OperationLogPO;
import com.apex.scaffold.infrastructure.persistence.po.SystemMessagePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

/**
 * 注册消费的欢迎消息与审计日志本地事务。
 *
 * <p>两次 Mapper 写入共享同步 JDBC 事务，不跨响应式仓储的独立调度边界。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Component
@RequiredArgsConstructor
public class UserRegistrationTransaction {
    private final SystemMessageMapper systemMessageMapper;
    private final SystemMessagePoConverter systemMessagePoConverter;
    private final OperationLogMapper operationLogMapper;
    private final OperationLogPoConverter operationLogPoConverter;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建延迟执行的双表写入，在 JDBC 专用池内提交或整体回滚。
     *
     * <p>捕获调用方租户并在工作线程恢复原上下文；每次订阅都会重新执行写入，
     * 调用方须在幂等消费边界内订阅。此事务不涵盖 Redis 消费状态。
     *
     * @param welcomeMessage 注册欢迎消息
     * @param operationLog 注册审计日志
     */
    public Mono<Void> execute(SystemMessageEntity welcomeMessage, OperationLogEntity operationLog) {
        String tenantId = TenantContext.currentTenantId().orElse(null);
        return Mono.fromRunnable(() -> {
                    String previousTenantId = TenantContext.setTenantId(tenantId);
                    try {
                        transactionTemplate.executeWithoutResult(status -> {
                            SystemMessagePO welcomePo = systemMessagePoConverter.toPo(welcomeMessage);
                            welcomePo.setDeleted(0);
                            systemMessageMapper.insert(welcomePo);

                            OperationLogPO logPo = operationLogPoConverter.toPo(operationLog);
                            operationLogMapper.insert(logPo);
                        });
                    } finally {
                        TenantContext.restoreTenantId(previousTenantId);
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }
}
