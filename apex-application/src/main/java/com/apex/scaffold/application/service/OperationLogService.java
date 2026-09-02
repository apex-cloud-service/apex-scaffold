package com.apex.scaffold.application.service;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.OperationLogEntity;
import com.apex.scaffold.domain.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 操作日志服务
 *
 * <p>提供操作日志的异步保存功能，使用 Reactor 响应式模型实现非阻塞写入。
 *
 * <h2>C3 并发修复（fire-subscribe / tenant 上下文）</h2>
 * <ul>
 *   <li>不再在调用线程里裸 {@code subscribe()}：通过
 *       {@link BlockingIoSchedulers#jdbc()} 受控调度落库，并写入线程
 *       显式安装/释放调用侧传入的 {@code tenantId}，避免 Reactor 线程切线程后
 *       ThreadLocal 串扰或丢失。</li>
 *   <li>使用 {@code usingWhen} 安装/恢复 TenantContext，即使取消/出错也保证清除。</li>
 * </ul>
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
     * 异步保存操作日志（不传入租户：使用 TenantContext 当前值）。
     *
     * <p>此重载仅在调用方必然运行在 TenantContext 已正确初始化的线程时安全，
     * 否则请使用 {@link #saveAsync(OperationLogEntity, String)}。
     *
     * @param operationLog 操作日志实体
     */
    public void saveAsync(OperationLogEntity operationLog) {
        saveAsync(operationLog, TenantContext.currentTenantId().orElse(null));
    }

    /**
     * 异步保存操作日志，显式指定落库时的租户 ID。
     *
     * <p>使用 Reactor 响应式模型异步写入操作日志，不阻塞调用线程；DB 写入在
     * 专用阻塞池 {@link BlockingIoSchedulers#jdbc()} 上执行，避免占用事件循环。
     *
     * @param operationLog 操作日志实体
     * @param tenantId     写入线程需要安装的租户 ID（允许为 null）
     */
    public void saveAsync(OperationLogEntity operationLog, String tenantId) {
        if (operationLog == null) {
            log.warn("OperationLogService.saveAsync 接收到空实体，已忽略");
            return;
        }
        saveInternal(operationLog, tenantId)
                .doOnSuccess(v -> log.debug("保存操作日志成功"))
                .doOnError(e -> log.error("保存操作日志失败: {}", e.getMessage(), e))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    /**
     * 构造写入管道（不 subscribe，便于单测组合与时序可控）。
     *
     * @param operationLog 操作日志实体
     * @param tenantId     安装的租户 ID
     * @return 组合后的 Mono（未订阅）
     */
    Mono<Void> saveInternal(OperationLogEntity operationLog, String tenantId) {
        // previous 可能为 null；usingWhen 会跳过 resourceClosure 当 resource 为 null。
        // 因此使用 1 元素数组保存 previous，resource 对象本身非空，保证三个回调都执行。
        String[] holder = new String[1];
        return Mono.usingWhen(
                        Mono.fromCallable(() -> {
                            holder[0] = TenantContext.setTenantId(tenantId);
                            return holder;
                        }),
                        ignored -> operationLogRepository
                                .insert(operationLog)
                                .then()
                                .subscribeOn(BlockingIoSchedulers.jdbc()),
                        ignored -> Mono.fromRunnable(() -> TenantContext.restoreTenantId(holder[0])),
                        (ignored, err) -> Mono.fromRunnable(() -> TenantContext.restoreTenantId(holder[0])),
                        ignored -> Mono.fromRunnable(() -> TenantContext.restoreTenantId(holder[0])))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
