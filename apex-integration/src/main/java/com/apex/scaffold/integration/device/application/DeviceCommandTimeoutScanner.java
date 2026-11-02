package com.apex.scaffold.integration.device.application;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.integration.device.domain.DeviceCommand;
import com.apex.scaffold.integration.device.domain.repository.DeviceCommandRepository;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 命令超时扫描任务（方案 M5 / 批次 E3）。
 *
 * <p>沿项目既有 {@code @Scheduled} 定时模式（参照 workflow 超时扫描）：进程内
 * {@link AtomicBoolean} 防同一 JVM 重入；扫描跨租户 PENDING/SENT 且
 * {@code timeout_at} 已过的命令，逐条在持久租户上下文中置 TIMEOUT。
 * jdbc 访问隔离由仓储层 {@code jdbc} 池承担。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
public class DeviceCommandTimeoutScanner {

    private final DeviceCommandRepository commandRepository;

    private final DeviceCommandService commandService;

    private final int batchSize;

    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * 构造命令超时扫描任务。
     *
     * @param commandRepository 命令仓储
     * @param commandService    命令用例
     * @param batchSize         单批扫描上限
     */
    public DeviceCommandTimeoutScanner(
            DeviceCommandRepository commandRepository,
            DeviceCommandService commandService,
            @Value("${apex.integration.iot.command.timeout-scan-batch:200}") int batchSize) {
        this.commandRepository = commandRepository;
        this.commandService = commandService;
        this.batchSize = Math.max(1, batchSize);
    }

    /** 本地定时桥接；失败仅在本调度边界记录，不影响下一轮。 */
    @Scheduled(
            fixedDelayString = "${apex.integration.iot.command.timeout-scan-interval-ms:30000}",
            initialDelayString = "${apex.integration.iot.command.timeout-scan-initial-delay-ms:15000}")
    public void scheduledScan() {
        scanDueCommands().subscribe(ignored -> {}, error -> log.error("命令超时扫描失败", error));
    }

    /**
     * 执行一轮超时扫描（可测试入口）。
     *
     * @return 本轮置 TIMEOUT 的命令数
     */
    public Mono<Long> scanDueCommands() {
        return Mono.defer(() -> {
            if (!running.compareAndSet(false, true)) {
                return Mono.just(0L);
            }
            return commandRepository
                    .findDueTimeout(batchSize)
                    .filter(command -> command.getTimeoutAt() != null
                            && !command.getTimeoutAt().isAfter(LocalDateTime.now()))
                    .flatMapDelayError(
                            command -> Mono.defer(() -> timeoutCommand(command))
                                    .contextWrite(TenantContext.withTenant(command.getTenantId())),
                            4,
                            4)
                    .count()
                    .doFinally(signal -> running.set(false));
        });
    }

    /** 单条超时迁移；单项失败延迟至其余命令处理完后统一传播。 */
    private Mono<DeviceCommand> timeoutCommand(DeviceCommand command) {
        return commandService
                .timeout(command)
                .doOnSuccess(ignored -> log.debug(
                        "命令超时已置 TIMEOUT: tenant={}, commandId={}", command.getTenantId(), command.getCommandId()))
                .thenReturn(command);
    }
}
