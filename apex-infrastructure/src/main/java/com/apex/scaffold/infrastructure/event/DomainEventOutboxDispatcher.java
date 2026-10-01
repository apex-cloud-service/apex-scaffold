package com.apex.scaffold.infrastructure.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.apex.scaffold.common.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 领域事件 Outbox 后台投递器。
 *
 * <p>每一批记录使用唯一领取令牌，监听器同步完成后才标记 DELIVERED。异常记录按指数退避
 * 重试，达到最大次数后进入 DEAD，供告警与人工补偿。事件构造仅允许
 * {@link AbstractDomainEvent} 子类，避免根据数据库内容实例化任意类型。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.2.0
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "apex.events.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DomainEventOutboxDispatcher {

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    private static final Logger log = LoggerFactory.getLogger(DomainEventOutboxDispatcher.class);

    /** Outbox 存储。 */
    private final DomainEventOutboxStore outboxStore;

    /** Spring 本地事件发布器。 */
    private final ApplicationEventPublisher eventPublisher;

    /** 单批最大事件数。 */
    @Value("${apex.events.outbox.batch-size:50}")
    private int batchSize;

    /** 最大投递次数。 */
    @Value("${apex.events.outbox.max-attempts:8}")
    private int maxAttempts;

    /** 处理锁超时秒数。 */
    @Value("${apex.events.outbox.lock-timeout-seconds:120}")
    private int lockTimeoutSeconds;

    /** 单实例 Outbox 投递并行度。 */
    @Value("${apex.events.outbox.parallelism:4}")
    private int parallelism;

    /** 是否启用 Outbox 保留策略清理。 */
    @Value("${apex.events.outbox.retention-enabled:true}")
    private boolean retentionEnabled;

    /** DELIVERED 记录保留小时数。 */
    @Value("${apex.events.outbox.retention-delivered-hours:168}")
    private int retentionDeliveredHours;

    /** DEAD 记录保留天数。 */
    @Value("${apex.events.outbox.retention-dead-days:7}")
    private int retentionDeadDays;

    /** 保留策略清理单批删除行数上限。 */
    @Value("${apex.events.outbox.retention-batch-size:5000}")
    private int retentionBatchSize;

    /** 保留策略清理最大批次数（单次清理总量上限 = batchSize × maxBatches）。 */
    private static final int RETENTION_MAX_BATCHES = 20;

    /** 在途投递批次计数（0 或 1），保证同一实例同一时刻最多一批投递在途。 */
    private final AtomicInteger inFlightBatches = new AtomicInteger();

    /** 有界批次共享的投递执行器。 */
    private volatile ExecutorService deliveryExecutor;

    /**
     * 领取并投递一批领域事件。
     *
     * <p>使用 {@link CompletableFuture#allOf} 并行投递本批所有事件，
     * 投递完成后通过 {@code whenComplete} 回调记录结果日志。
     *
     * <h2>优化说明（Netty/线程池/IO 性能优化方案 P0-2）</h2>
     * <p>原实现使用 {@code CompletableFuture.allOf(deliveries).join()}，
     * 在单线程 {@code @Scheduled} 调度器下会阻塞调度线程直到整批投递完成，
     * 导致投票预取补位、ELO 分桶刷新等其他定时任务被延迟。
     * 改为非阻塞 {@code whenComplete} 回调模式：
     * <ul>
     *   <li>投递线程继续并行执行本批事件，不阻塞调度线程</li>
     *   <li>通过 {@code orTimeout} 设置锁超时兜底，防止异常情况下无限等待</li>
     *   <li>回调中仅记录日志，主逻辑与异常分离</li>
     *   <li>{@code records} 作为局部变量被 lambda 闭包持有引用，不会被 GC 提前回收</li>
     * </ul></p>
     *
     * <h2>批次互斥</h2>
     * <p>上一批仍在投递（{@code whenComplete} 未回调）时，本轮跳过领取，保证
     * 同一实例同一时刻最多一批投递在途，避免投递执行器无界堆积，与配置注释
     * "单批仍等待全部完成后再进入下一轮领取"保持一致。</p>
     */
    @Scheduled(fixedDelayString = "${apex.events.outbox.poll-delay-ms:1000}")
    public void dispatch() {
        if (!inFlightBatches.compareAndSet(0, 1)) {
            log.debug("上一批 Outbox 投递仍在进行中，跳过本轮领取");
            return;
        }
        try {
            String workerId = UUID.randomUUID().toString();
            List<OutboxEventRecord> records = outboxStore.claim(workerId, batchSize, lockTimeoutSeconds);
            if (records.isEmpty()) {
                inFlightBatches.set(0);
                return;
            }
            CompletableFuture<?>[] deliveries = records.stream()
                    .map(record -> CompletableFuture.runAsync(() -> deliver(record, workerId), deliveryExecutor()))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(deliveries)
                    .orTimeout(lockTimeoutSeconds, TimeUnit.SECONDS)
                    .whenComplete((result, ex) -> {
                        inFlightBatches.set(0);
                        if (ex != null) {
                            log.error("Outbox 批次投递异常, workerId={}, batchSize={}", workerId, records.size(), ex);
                        } else {
                            log.debug("Outbox 批次投递完成, workerId={}, batchSize={}", workerId, records.size());
                        }
                    });
        } catch (RuntimeException exception) {
            inFlightBatches.set(0);
            throw exception;
        }
    }

    /**
     * 按保留策略清理已投递与死信记录。
     *
     * <p>防止 {@code sys_domain_event_outbox} 无限增长：DELIVERED 与 DEAD 记录投递后
     * 不再参与消费，定期分批删除。清理失败抛出异常由调度框架记录，下轮重试。</p>
     */
    @Scheduled(fixedDelayString = "${apex.events.outbox.retention-interval-ms:3600000}")
    public void purgeRetained() {
        if (!retentionEnabled) {
            return;
        }
        int delivered = outboxStore.deleteDeliveredOlderThan(
                retentionDeliveredHours, retentionBatchSize, RETENTION_MAX_BATCHES);
        int dead = outboxStore.deleteDeadOlderThan(retentionDeadDays, retentionBatchSize, RETENTION_MAX_BATCHES);
        if (delivered > 0 || dead > 0) {
            log.info("Outbox 保留策略清理完成: DELIVERED={}, DEAD={}", delivered, dead);
        }
    }

    /**
     * 获取投递执行器，首次访问时按配置并行度惰性创建。
     *
     * <p>线程名使用 {@code outbox-delivery-N} 前缀并注册未捕获异常处理器，
     * 便于日志定位与故障排查。</p>
     *
     * @return 有界并行度的投递执行器
     */
    private ExecutorService deliveryExecutor() {
        ExecutorService current = deliveryExecutor;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (deliveryExecutor == null) {
                int configuredParallelism = Math.max(1, parallelism);
                AtomicInteger sequence = new AtomicInteger();
                int corePoolSize = configuredParallelism;
                deliveryExecutor = new java.util.concurrent.ThreadPoolExecutor(
                        corePoolSize,
                        corePoolSize,
                        0L,
                        java.util.concurrent.TimeUnit.MILLISECONDS,
                        new java.util.concurrent.LinkedBlockingQueue<>(),
                        task -> {
                            Thread thread = new Thread(task, "outbox-delivery-" + sequence.incrementAndGet());
                            thread.setUncaughtExceptionHandler(
                                    (worker, error) -> log.error("Outbox 投递线程异常, thread={}", worker.getName(), error));
                            return thread;
                        });
            }
            return deliveryExecutor;
        }
    }

    /** 停机时停止领取新任务并等待当前批次完成。 */
    @PreDestroy
    public void shutdown() {
        ExecutorService current = deliveryExecutor;
        if (current == null) {
            return;
        }
        current.shutdown();
        try {
            if (!current.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                current.shutdownNow();
            }
        } catch (InterruptedException exception) {
            current.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** 投递单条记录并更新结果。 */
    private void deliver(OutboxEventRecord record, String workerId) {
        try {
            AbstractDomainEvent event = restore(record);
            eventPublisher.publishEvent(event);
            outboxStore.markDelivered(record.id(), workerId);
            log.info("领域事件投递完成, eventId: {}, eventType: {}", record.eventId(), record.eventClass());
        } catch (Exception exception) {
            String error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            outboxStore.markFailed(record, workerId, maxAttempts, error);
            log.error("领域事件投递失败, eventId: {}, attempts: {}", record.eventId(), record.attempts() + 1, exception);
        }
    }

    /**
     * 从 Outbox 记录恢复领域事件实例。
     *
     * <p>优先使用 Jackson 直接反序列化（需 {@code @JsonCreator} 注解），
     * 失败时回退到反射构造器参数名匹配（需 {@code -parameters} 编译选项）。</p>
     *
     * @param record Outbox 事件记录
     * @return 反序列化后的领域事件实例
     * @throws Exception 反序列化失败时抛出
     */
    private AbstractDomainEvent restore(OutboxEventRecord record) throws Exception {
        Class<?> rawType = Class.forName(record.eventClass());
        if (!AbstractDomainEvent.class.isAssignableFrom(rawType)) {
            throw new IllegalArgumentException("Outbox 事件类型不受支持: " + record.eventClass());
        }
        @SuppressWarnings("unchecked")
        Class<? extends AbstractDomainEvent> eventType = (Class<? extends AbstractDomainEvent>) rawType;

        // 优先 Jackson（需要 @JsonCreator + @JsonProperty）
        try {
            return JsonUtil.getObjectMapper().readValue(record.payload(), eventType);
        } catch (Exception jacksonError) {
            log.debug("Jackson 反序列化失败，回退到反射构造器: eventType={}", record.eventClass(), jacksonError);
        }

        // 回退：反射构造器参数名匹配（需要 -parameters）
        return restoreByReflection(record.payload(), eventType);
    }

    /** 反射回退：通过构造器参数名匹配 JSON 字段恢复事件。 */
    private AbstractDomainEvent restoreByReflection(String payload, Class<? extends AbstractDomainEvent> eventType)
            throws Exception {
        Constructor<?> constructor = java.util.Arrays.stream(eventType.getDeclaredConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow(() -> new IllegalArgumentException("领域事件缺少构造函数"));
        JsonNode root = JsonUtil.getObjectMapper().readTree(payload);
        java.lang.reflect.Parameter[] parameters = constructor.getParameters();
        Object[] arguments = new Object[parameters.length];
        for (int index = 0; index < parameters.length; index++) {
            java.lang.reflect.Parameter parameter = parameters[index];
            JsonNode value = root.get(parameter.getName());
            arguments[index] = value == null || value.isNull()
                    ? null
                    : JsonUtil.getObjectMapper().convertValue(value, parameter.getType());
        }
        constructor.setAccessible(true);
        return (AbstractDomainEvent) constructor.newInstance(arguments);
    }
}
