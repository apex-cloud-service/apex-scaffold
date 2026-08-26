package com.apex.scaffold.application.service;

import com.apex.scaffold.domain.model.entity.OperationLogEntity;
import com.apex.scaffold.domain.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 操作日志服务
 *
 * <p>提供操作日志的异步保存功能，使用 Reactor 响应式模型实现非阻塞写入。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    /**
     * 操作日志仓储。
     *
     * <p>用于异步持久化用户操作记录，如登录、资料修改、权限变更等。</p>
     */
    private final OperationLogRepository operationLogRepository;

    /**
     * 异步保存操作日志
     *
     * <p>使用 Reactor 响应式模型异步写入操作日志，不阻塞调用线程。
     *
     * @param operationLog 操作日志实体
     */
    public void saveAsync(OperationLogEntity operationLog) {
        operationLogRepository
                .insert(operationLog)
                .doOnSuccess(unused -> log.debug("保存操作日志成功"))
                .subscribe(null, e -> log.error("保存操作日志失败: {}", e.getMessage(), e));
    }
}
