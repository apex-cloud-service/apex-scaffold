package com.apex.scaffold.infrastructure.mq.listener;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.infrastructure.mq.constant.MqConstant;
import com.apex.scaffold.infrastructure.mq.metrics.MqConsumerMetrics;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 死信队列（DLQ）自动处理器。
 *
 * <p>自动监听 RocketMQ 死信队列中的消息，对消费重试耗尽的消息进行二次处理、
 * 告警通知和持久化记录，避免死信消息只能人工查询处理的问题。
 *
 * <h2>设计说明</h2>
 * <ol>
 *   <li>消费者拉取到死信消息后，解析原始 Topic 和消息体</li>
 *   <li>根据原始 Topic 路由到对应的 {@link DlqHandler} 处理器</li>
 *   <li>处理器执行补偿逻辑（如重投递、降级处理、写入审计表）</li>
 *   <li>补偿成功则 ACK，失败则记录 CRITICAL 告警日志</li>
 * </ol>
 *
 * <h2>设计说明</h2>
 * <p>需同时满足以下条件：
 * <ul>
 *   <li>RocketMQ 已配置（{@code rocketmq.name-server} 不为空）</li>
 *   <li>{@code apex.mq.dlq.enabled=true}（默认 true）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see MqConstant
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "apex.features", name = "message-queue", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(RocketMQAutoConfiguration.class)
@ConditionalOnProperty(name = "apex.mq.consumer.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "apex.mq.dlq.enabled", havingValue = "true", matchIfMissing = true)
public class DeadLetterQueueProcessor {

    /** 死信 Topic 前缀（RocketMQ 规范：%DLQ% + 消费组名） */
    private static final String DLQ_TOPIC_PREFIX = "%DLQ%";

    /** RocketMQ NameServer 地址 */
    private final String nameServer;

    /** 死信处理器自身的消费者组名，可通过 {@code apex.mq.dlq.consumer-group} 覆盖。 */
    private final String dlqConsumerGroup;

    /** DLQ 处理器是否已启动 */
    private volatile boolean started = false;

    private boolean lifecycleInitialized = false;

    /** 原始 Topic 到死信处理器的路由表 */
    private final Map<String, DlqHandler> handlerRegistry = new ConcurrentHashMap<>();

    private final Map<String, String> consumerGroupRegistry = new ConcurrentHashMap<>();

    /** 死信 Topic 到原消费组的反查表，用于自然死信消息唯一确定原始业务 Topic。 */
    private final Map<String, String> dlqTopicConsumerGroups = new ConcurrentHashMap<>();

    private final Set<String> subscribedDlqTopics = ConcurrentHashMap.newKeySet();

    private final Object lifecycleMonitor = new Object();

    /** 是否继续接受死信处理回调。 */
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    /** 当前正在执行的死信处理数量。 */
    private final AtomicInteger inFlight = new AtomicInteger();

    /** RocketMQ 死信消费者实例 */
    private DefaultMQPushConsumer dlqConsumer;

    /** 消费者监控指标（用于死信告警与统计） */
    @Nullable
    private final MqConsumerMetrics consumerMetrics;

    /**
     * 构造死信队列处理器。
     *
     * @param nameServer RocketMQ NameServer 地址，默认空字符串表示未配置（处理器不启动）
     * @param dlqConsumerGroup 死信处理器自身的消费者组名，默认 {@code apex-dlq-processor-dlq-consumer}
     * @param consumerMetrics 消费者监控指标
     */
    public DeadLetterQueueProcessor(
            @Value("${rocketmq.name-server:}") String nameServer,
            @Value("${apex.mq.dlq.consumer-group:apex-dlq-processor-dlq-consumer}") String dlqConsumerGroup,
            MqConsumerMetrics consumerMetrics) {
        this.nameServer = nameServer;
        this.dlqConsumerGroup = dlqConsumerGroup;
        this.consumerMetrics = consumerMetrics;
    }

    /**
     * 注册死信处理器。
     *
     * <p>各业务模块通过此方法注册其 Topic 的死信处理逻辑。
     * 在死信消息到达时，根据原始 Topic 路由到对应处理器。
     *
     * @param originalTopic 原始业务 Topic
     * @param handler       死信处理器
     */
    public void registerHandler(String originalTopic, DlqHandler handler) {
        registerHandler(originalTopic, originalTopic.replace("-topic", "-consumer-group"), handler);
    }

    /**
     * 注册死信处理器（指定消费组）。
     *
     * <p>显式指定原始 Topic 对应的消费组，用于处理 RocketMQ 重试耗尽后投递到
     * {@code %DLQ% + 消费组名} 死信 Topic 的消息。注册发生在启动前的，启动时自动订阅；
     * 注册发生在启动后的，立即补订阅该 Topic。</p>
     *
     * @param originalTopic 原始业务 Topic
     * @param consumerGroup 原始消费组名（用于推导死信 Topic）
     * @param handler       死信处理器
     */
    public void registerHandler(String originalTopic, String consumerGroup, DlqHandler handler) {
        synchronized (lifecycleMonitor) {
            registerHandlerAndSubscribe(originalTopic, consumerGroup, handler);
        }
    }

    /**
     * 声明业务消费组订阅的原始 Topic，供死信路由使用，不注册处理器。
     *
     * <p>RocketMQ 自然死信 Topic 按消费组生成（{@code %DLQ% + 消费组名}）。需声明该组订阅的
     * 全部业务 Topic；消息缺少原始 Topic 属性时，仅有一个候选 Topic 才能安全反查。
     *
     * @param originalTopic 业务消费组订阅的原始业务 Topic
     * @param consumerGroup 原始消费组名
     */
    public void registerConsumerGroup(String originalTopic, String consumerGroup) {
        synchronized (lifecycleMonitor) {
            bindConsumerGroup(originalTopic, consumerGroup);
            log.info("声明 DLQ 消费组映射, topic: {}, consumerGroup: {}", originalTopic, consumerGroup);
        }
    }

    /**
     * 绑定原始 Topic 与消费组，并维护死信 Topic 反查表。
     *
     * <p>保留同一消费组的全部 Topic 绑定，避免将缺少原始 Topic 属性的死信误路由到
     * 最后登记的 Topic。</p>
     *
     * <p>调用方须持有 {@link #lifecycleMonitor}。
     *
     * @param originalTopic 原始业务 Topic
     * @param consumerGroup 原始消费组名
     */
    private void bindConsumerGroup(String originalTopic, String consumerGroup) {
        consumerGroupRegistry.put(originalTopic, consumerGroup);
        dlqTopicConsumerGroups.put(DLQ_TOPIC_PREFIX + consumerGroup, consumerGroup);
    }

    /**
     * 登记处理器与消费组映射，并按当前生命周期状态补订阅。
     *
     * <p>调用方须持有 {@link #lifecycleMonitor}。
     *
     * @param originalTopic 原始业务 Topic
     * @param consumerGroup 原始消费组名
     * @param handler 死信处理器
     */
    private void registerHandlerAndSubscribe(String originalTopic, String consumerGroup, DlqHandler handler) {
        handlerRegistry.put(originalTopic, handler);
        bindConsumerGroup(originalTopic, consumerGroup);
        log.info(
                "注册 DLQ 处理器, topic: {}, consumerGroup: {}, handler: {}",
                originalTopic,
                consumerGroup,
                handler.getClass().getSimpleName());
        if (started) {
            subscribe(originalTopic);
        } else if (lifecycleInitialized) {
            startIfReady();
        }
    }

    /**
     * 启动死信队列消费者。
     *
     * <p>在 Bean 初始化后自动启动，监听所有已注册消费组的死信 Topic。
     * 如果 NameServer 未配置或无注册处理器，则跳过启动。
     * 启动过程中发生的 {@link MQClientException} 由内部捕获并记录日志，不向调用方透传；
     * 启动失败期间，死信消息无法由本处理器自动处理。
     */
    @PostConstruct
    public void start() {
        accepting.set(true);
        synchronized (lifecycleMonitor) {
            lifecycleInitialized = true;
            startIfReady();
        }
    }

    /**
     * 在启动条件满足且消费者尚未启动时，同步初始化并启动死信消费者。
     *
     * <p>由持有生命周期监视器的调用方执行；未配置 NameServer 或无处理器时直接返回。
     * 客户端启动可能阻塞当前线程。捕获 {@link MQClientException} 后清空本地订阅记录和
     * 消费者引用并记录失败，保留未启动状态，供后续注册处理器时再次尝试。
     */
    private void startIfReady() {
        if (started || nameServer == null || nameServer.isBlank()) {
            if (nameServer == null || nameServer.isBlank()) {
                log.info("RocketMQ NameServer 未配置, DLQ 处理器不启动");
            }
            return;
        }
        if (handlerRegistry.isEmpty()) {
            log.info("无已注册的 DLQ 处理器, DLQ 处理器暂不启动");
            return;
        }
        try {
            initAndStartConsumer();
        } catch (MQClientException e) {
            subscribedDlqTopics.clear();
            dlqConsumer = null;
            log.error("DLQ 消费者启动失败, 死信消息将无法自动处理", e);
        }
    }

    /**
     * 初始化并启动死信消费者。
     *
     * @throws MQClientException 如果消费者启动失败
     */
    private void initAndStartConsumer() throws MQClientException {
        dlqConsumer = new DefaultMQPushConsumer(dlqConsumerGroup);
        dlqConsumer.setNamesrvAddr(nameServer);

        for (String topic : handlerRegistry.keySet()) {
            subscribe(topic);
        }

        dlqConsumer.registerMessageListener(new DlqMessageListener());
        dlqConsumer.start();
        started = true;
        log.info("DLQ 死信队列处理器已启动, 监听 {} 个 Topic", subscribedDlqTopics.size());
    }

    /**
     * 为原始业务 Topic 对应的消费组订阅死信 Topic，接收全部标签。
     *
     * <p>相同死信 Topic 只订阅一次。订阅时发生的 {@link MQClientException} 在内部记录，
     * 同时撤销去重标记，以允许后续再次尝试订阅。
     *
     * @param originalTopic 已登记消费组映射的原始业务 Topic
     */
    private void subscribe(String originalTopic) {
        String dlqTopic = buildDlqTopic(originalTopic);
        if (!subscribedDlqTopics.add(dlqTopic)) {
            return;
        }
        try {
            dlqConsumer.subscribe(dlqTopic, "*");
            log.info("订阅死信 Topic: {}", dlqTopic);
        } catch (MQClientException e) {
            subscribedDlqTopics.remove(dlqTopic);
            log.error("订阅死信 Topic 失败: {}", dlqTopic, e);
        }
    }

    /**
     * 构建死信 Topic 名称。
     *
     * <p>RocketMQ 死信 Topic 格式为 {@code %DLQ% + 消费组名}。
     * 本方法根据原始 Topic 查找对应的消费组死信 Topic。
     *
     * @param originalTopic 原始业务 Topic
     * @return 死信 Topic 名称
     */
    private String buildDlqTopic(String originalTopic) {
        return DLQ_TOPIC_PREFIX + consumerGroupRegistry.get(originalTopic);
    }

    /**
     * 停止接收新的死信处理回调。
     */
    public void stopAccepting() {
        accepting.set(false);
    }

    /**
     * 在截止时间前等待在途死信处理完成。
     *
     * @param deadline 统一截止时间
     * @return true 表示已排空
     */
    public CompletionStage<Boolean> drain(Instant deadline) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        drainUntil(deadline, result);
        return result;
    }

    /**
     * 检查在途死信处理是否排空，未完成时延迟约十毫秒再次检查，不阻塞等待。
     *
     * <p>先检查在途数量，为零则完成为 {@code true}；仍有任务且到达截止时间时完成为
     * {@code false}。本方法不停止接收新任务，调用方需另行控制接收状态。
     *
     * @param deadline 排空等待的截止时间
     * @param result 承载排空结果的异步完成对象
     */
    private void drainUntil(Instant deadline, CompletableFuture<Boolean> result) {
        if (inFlight.get() == 0) {
            result.complete(true);
            return;
        }
        if (!Instant.now().isBefore(deadline)) {
            result.complete(false);
            return;
        }
        CompletableFuture.delayedExecutor(10, java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> drainUntil(deadline, result));
    }

    /**
     * 强制停止死信消费者。
     */
    public void forceStop() {
        shutdown();
    }

    /**
     * 处理单条死信消息。
     *
     * <p>根据消息的原始 Topic 路由到对应的处理器。无法唯一解析 Topic 或未找到处理器时，
     * 记录告警并请求稍后重投，不将未完成的补偿确认为成功。
     *
     * @param messageExt RocketMQ 消息体
     * @return 处理结果
     */
    ConsumeConcurrentlyStatus processDeadLetter(MessageExt messageExt) {
        if (!accepting.get()) {
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
        String tenantId = messageExt.getUserProperty("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            log.error("[DLQ] 消息缺少租户，拒绝处理, msgId: {}", messageExt.getMsgId());
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
        inFlight.incrementAndGet();
        String previousTenantId = TenantContext.setTenantId(tenantId);
        try {
            return processDeadLetterInternal(messageExt);
        } finally {
            TenantContext.restoreTenantId(previousTenantId);
            inFlight.decrementAndGet();
        }
    }

    /**
     * 解析死信消息的原始业务 Topic。
     *
     * <p>RocketMQ 自然死信消息由 Broker 投递到 {@code %DLQ%<消费组>}，消息自身的 Topic 是死信 Topic
     * 而非业务 Topic。不同重试路径不保证保留相同属性，因此按以下优先级解析：
     * <ol>
     *   <li>发送方显式设置的 {@code ORIGINAL_TOPIC} 属性</li>
     *   <li>Broker 写入的 {@link MessageConst#PROPERTY_RETRY_TOPIC} 属性</li>
     *   <li>死信 Topic 反查：未配置 {@code ORIGINAL_TOPIC} 且 Broker 属性缺失时，按订阅时记录的
     *       死信 Topic 到业务 Topic 映射唯一匹配</li>
     * </ol>
     *
     * <p>死信 Topic 按消费组生成，同一消费组订阅多个业务 Topic 时无法唯一反查，此时返回 {@code null}，
     * 由调用方按未匹配处理，避免错误路由到其他 Topic 的处理器。
     *
     * @param messageExt 待处理的死信消息
     * @return 原始业务 Topic；无法唯一确定时返回 {@code null}
     */
    private String resolveOriginalTopic(MessageExt messageExt) {
        String explicitTopic = messageExt.getUserProperty("ORIGINAL_TOPIC");
        if (explicitTopic != null && !explicitTopic.isBlank()) {
            return explicitTopic;
        }
        String retryTopic = messageExt.getProperty(MessageConst.PROPERTY_RETRY_TOPIC);
        if (retryTopic != null && !retryTopic.isBlank()) {
            return retryTopic;
        }
        String dlqTopic = messageExt.getTopic();
        String consumerGroup = dlqTopicConsumerGroups.get(dlqTopic);
        List<String> candidates = consumerGroupRegistry.entrySet().stream()
                .filter(entry -> entry.getValue().equals(consumerGroup))
                .map(Map.Entry::getKey)
                .toList();
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        log.error(
                "[DLQ] 无法唯一解析原始 Topic, msgId: {}, dlqTopic: {}, 候选 Topic: {}",
                messageExt.getMsgId(),
                dlqTopic,
                candidates);
        return null;
    }

    /**
     * 按原始 Topic 同步执行死信处理器，并将处理结果映射为消费确认状态。
     *
     * <p>原始 Topic 由 {@link #resolveOriginalTopic(MessageExt)} 解析。未找到处理器、处理器返回失败
     * 或执行时抛出 {@link Exception} 均请求稍后重投。处理器直接在当前回调线程执行，可能阻塞。
     *
     * @param messageExt 待处理的 RocketMQ 死信消息
     * @return 仅处理成功时返回消费成功，否则返回稍后重投
     */
    private ConsumeConcurrentlyStatus processDeadLetterInternal(MessageExt messageExt) {
        String originalTopic = resolveOriginalTopic(messageExt);
        String msgId = messageExt.getMsgId();
        int reconsumeTimes = messageExt.getReconsumeTimes();
        log.warn(
                "[DLQ] 收到死信消息, msgId: {}, originalTopic: {}, reconsumeTimes: {}", msgId, originalTopic, reconsumeTimes);
        if (consumerMetrics != null) {
            consumerMetrics.recordDlq();
        }

        DlqHandler handler = originalTopic == null ? null : handlerRegistry.get(originalTopic);
        if (handler == null) {
            log.error("[DLQ] 未找到 Topic {} 的死信处理器, msgId: {}, 请求稍后重投", originalTopic, msgId);
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
        try {
            boolean success = handler.handle(messageExt);
            if (success) {
                log.info("[DLQ] 死信消息处理成功, msgId: {}, topic: {}", msgId, originalTopic);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            } else {
                log.error("[DLQ] 死信消息处理失败, msgId: {}, topic: {}", msgId, originalTopic);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        } catch (Exception e) {
            log.error("[DLQ] 死信消息处理异常, msgId: {}, topic: {}", msgId, originalTopic, e);
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }

    /**
     * 关闭死信消费者。
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        accepting.set(false);
        synchronized (lifecycleMonitor) {
            if (dlqConsumer != null && started) {
                dlqConsumer.shutdown();
                started = false;
                subscribedDlqTopics.clear();
                log.info("DLQ 死信队列处理器已关闭");
            }
        }
    }

    /**
     * 死信消息监听器内部实现。
     *
     * <p>将 RocketMQ 原生消息监听回调委托给外部处理器的内部桥接类。
     *
     * @author Apex Team
     */
    private class DlqMessageListener implements MessageListenerConcurrently {

        /**
         * 消费死信消息。
         *
         * @param msgs    消息列表
         * @param context 消费上下文
         * @return 消费状态
         */
        @Override
        public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
            for (MessageExt msg : msgs) {
                ConsumeConcurrentlyStatus status = processDeadLetter(msg);
                if (status != ConsumeConcurrentlyStatus.CONSUME_SUCCESS) {
                    return status;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
    }

    /**
     * 死信消息处理器接口。
     *
     * <p>各业务模块实现此接口，定义其 Topic 消费失败后的补偿逻辑。
     *
     * @author Apex Team
     */
    @FunctionalInterface
    public interface DlqHandler {

        /**
         * 处理死信消息。
         *
         * @param messageExt RocketMQ 原始消息体
         * @return true 表示处理成功（ACK），false 表示处理失败（重新投递）
         */
        boolean handle(MessageExt messageExt);
    }
}
