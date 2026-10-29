package com.apex.scaffold.application.listener;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.event.BaseDomainEvent;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.event.CreditConsumedEvent;
import com.apex.scaffold.domain.event.CreditEarnedEvent;
import com.apex.scaffold.domain.model.entity.SystemMessageEntity;
import com.apex.scaffold.domain.repository.SystemMessageRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 积分事件监听器
 *
 * <p>处理积分相关的领域事件（消费低余额告警、赚取里程碑通知）。与 C2 修复一致：
 * 不使用 .block() 阻塞发布线程；管道内 withTenantFromEvent 显式安装/恢复事件 DTO
 * 带的 tenantId，避免发布线程切换后的 ThreadLocal 丢失。</p>
 *
 * @author Apex Team
 * @version 3.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
public class CreditEventListener {

    /** 低余额告警阈值。 */
    private static final long LOW_BALANCE_THRESHOLD = 10L;

    /** 里程碑积分节点。 */
    private static final long[] MILESTONE_POINTS = {100L, 500L, 1000L, 5000L};

    private final SystemMessageRepository systemMessageRepository;

    /**
     * 创建监听器，系统消息仓储可选注入，未注入时跳过通知写入。
     *
     * @param systemMessageRepository 系统消息仓储，可选
     */
    public CreditEventListener(Optional<SystemMessageRepository> systemMessageRepository) {
        this.systemMessageRepository = systemMessageRepository.orElse(null);
    }

    /**
     * 监听积分消费事件，剩余余额低于阈值时异步写入低余额系统消息提醒。
     *
     * @param event 积分消费领域事件
     */
    @EventListener(CreditConsumedEvent.class)
    public void handleCreditConsumedEvent(CreditConsumedEvent event) {
        log.info(
                "处理积分消费事件: userId={}, amount={}, remainingBalance={}",
                event.getUserId(),
                event.getAmount(),
                event.getRemainingBalance());
        runWithTenantFromEvent(event, () -> handleConsumedAsync(event))
                .doOnSuccess(v -> log.info("积分消费事件处理完成: userId={}", event.getUserId()))
                .doOnError(e -> log.error("handleCreditConsumedEvent 异常: userId={}", event.getUserId(), e))
                .subscribe();
    }

    /**
     * 监听积分赚取事件，跨越里程碑节点时异步写入里程碑祝贺系统消息。
     *
     * @param event 积分赚取领域事件
     */
    @EventListener(CreditEarnedEvent.class)
    public void handleCreditEarnedEvent(CreditEarnedEvent event) {
        log.info(
                "处理积分赚取事件: userId={}, amount={}, totalBalance={}, source={}",
                event.getUserId(),
                event.getAmount(),
                event.getTotalBalance(),
                event.getSource());
        runWithTenantFromEvent(event, () -> handleEarnedAsync(event))
                .doOnSuccess(v -> log.info("积分赚取事件处理完成: userId={}", event.getUserId()))
                .doOnError(e -> log.error("handleCreditEarnedEvent 异常: userId={}", event.getUserId(), e))
                .subscribe();
    }

    private Mono<Void> handleConsumedAsync(CreditConsumedEvent event) {
        if (event.getRemainingBalance() != null
                && event.getRemainingBalance() <= LOW_BALANCE_THRESHOLD
                && systemMessageRepository != null) {
            SystemMessageEntity warning = new SystemMessageEntity();
            warning.setUserId(event.getUserId());
            warning.setMsgType("LOW_CREDIT");
            warning.setContent(
                    "您的积分余额已不足 " + LOW_BALANCE_THRESHOLD + "，当前余额: " + event.getRemainingBalance() + "，快去赚取更多积分吧！");
            warning.setRefType("CREDIT");
            warning.setCreateTime(LocalDateTime.now());
            warning.setUpdateTime(LocalDateTime.now());
            return systemMessageRepository
                    .save(warning)
                    .doOnSuccess(v -> log.info(
                            "低余额提醒发送成功: userId={}, balance={}", event.getUserId(), event.getRemainingBalance()))
                    .doOnError(e -> log.error("低余额提醒发送失败: userId={}", event.getUserId(), e))
                    .onErrorResume(e -> Mono.empty())
                    .then();
        } else if (systemMessageRepository == null) {
            log.debug("SystemMessageRepository 未注入，跳过低余额提醒");
        }
        return Mono.empty();
    }

    private Mono<Void> handleEarnedAsync(CreditEarnedEvent event) {
        if (event.getTotalBalance() == null || systemMessageRepository == null) {
            if (systemMessageRepository == null) {
                log.debug("SystemMessageRepository 未注入，跳过里程碑检测");
            }
            return Mono.empty();
        }
        long balance = event.getTotalBalance();
        List<Mono<Void>> writes = new ArrayList<>(1);
        for (long milestone : MILESTONE_POINTS) {
            Long prev = balance - event.getAmount();
            if (prev != null && prev < milestone && balance >= milestone) {
                SystemMessageEntity milestoneMsg = new SystemMessageEntity();
                milestoneMsg.setUserId(event.getUserId());
                milestoneMsg.setMsgType("CREDIT_MILESTONE");
                milestoneMsg.setContent("恭喜！您的积分已达到 " + milestone + "！继续加油！");
                milestoneMsg.setRefType("CREDIT");
                milestoneMsg.setCreateTime(LocalDateTime.now());
                milestoneMsg.setUpdateTime(LocalDateTime.now());
                writes.add(systemMessageRepository
                        .save(milestoneMsg)
                        .doOnSuccess(v -> log.info("里程碑提醒发送成功: userId={}, milestone={}", event.getUserId(), milestone))
                        .doOnError(e -> log.error("里程碑提醒发送失败: userId={}", event.getUserId(), e))
                        .onErrorResume(e -> Mono.empty())
                        .then());
                break;
            }
        }
        return writes.isEmpty() ? Mono.empty() : Mono.when(writes);
    }

    private Mono<Void> runWithTenantFromEvent(BaseDomainEvent event, Supplier<Mono<Void>> task) {
        String tenantId = event.getTenantId();
        // usingWhen 的 resource 不能为 null，用 String[1] 作 previous 非空 holder。
        String[] holder = new String[1];
        return Mono.usingWhen(
                        Mono.fromCallable(() -> {
                            holder[0] = TenantContext.setTenantId(tenantId);
                            return holder;
                        }),
                        ignored -> task.get(),
                        ignored -> Mono.fromRunnable(() -> TenantContext.restoreTenantId(holder[0])),
                        (ignored, err) -> Mono.fromRunnable(() -> TenantContext.restoreTenantId(holder[0])),
                        ignored -> Mono.fromRunnable(() -> TenantContext.restoreTenantId(holder[0])))
                .subscribeOn(BlockingIoSchedulers.event());
    }
}
