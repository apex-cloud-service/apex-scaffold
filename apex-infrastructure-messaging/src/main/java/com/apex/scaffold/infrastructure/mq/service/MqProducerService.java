package com.apex.scaffold.infrastructure.mq.service;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.infrastructure.outbox.entity.MessageOutboxEntity;
import com.apex.scaffold.infrastructure.outbox.service.MessageOutboxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * MQ生产者服务
 *
 * <p>提供RocketMQ消息发送的多种模式，支持同步、异步、单向和延迟消息发送。
 * 所有同步发送方法均包含异常捕获，避免RocketMQ运行时异常直接传播到调用方。
 *
 * <h2>支持的发送模式</h2>
 * <ul>
 *   <li><b>同步发送</b>：等待消息发送完成后返回结果，带异常处理和超时控制</li>
 *   <li><b>异步发送</b>：通过回调函数通知发送结果，不阻塞调用线程</li>
 *   <li><b>单向发送</b>：只管发送不等待结果，适用于日志等可靠性要求不高的场景</li>
 *   <li><b>延迟发送</b>：在指定延迟等级后投递消息，带超时控制</li>
 *   <li><b>带Header发送</b>：发送消息时附带自定义Header信息</li>
 * </ul>
 *
 * <h2>异常处理</h2>
 * <p>同步发送方法（{@link #sendSync}, {@link #sendDelay}）在RocketMQ客户端抛出
 * 异常时记录错误日志并重新抛出，避免静默丢失消息。上层调用方应做好异常处理。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * // 同步发送
 * SendResult result = mqProducerService.sendSync(topic, tag, message);
 *
 * // 异步发送
 * mqProducerService.sendAsync(topic, tag, message, new SendCallback() {...});
 *
 * }</pre>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 * @see RocketMQTemplate
 * @see SendResult
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "apex.features", name = "message-queue", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnBean(RocketMQTemplate.class)
@Slf4j
public class MqProducerService {
    /** 同步发送超时时间（毫秒） */
    private static final long SYNC_SEND_TIMEOUT_MS = 30000L;

    private static final String MESSAGE_ID_HEADER = "apex-message-id";
    private static final String TENANT_ID_HEADER = "tenantId";
    private static final String ROCKETMQ_KEYS_HEADER = "KEYS";
    /** JSON 序列化中 null 值的字符串表示。 */
    private static final String NULL_STRING = "null";

    /**
     * RocketMQ 模板
     *
     * <p>用于发送 RocketMQ 消息，提供同步、异步、单向和事务等多种发送方式。
     */
    private final RocketMQTemplate rocketMQTemplate;

    /** JSON 序列化器，用于消息体写入发件箱前的持久化 */
    private final ObjectMapper objectMapper;

    /** 消息发件箱服务，用于投递状态管理与发送失败兜底 */
    private final MessageOutboxService messageOutboxService;

    /**
     * 同步发送消息
     *
     * <p>以同步方式向指定Topic和Tag发送消息，等待Broker确认后返回发送结果。
     * 发送过程带异常捕获，RocketMQ客户端异常会记录错误日志并重新抛出。
     *
     * @param <T>     消息体类型
     * @param topic   消息主题，不能为空
     * @param tag     消息标签，用于消息过滤和分类
     * @param message 消息体对象
     * @return SendResult 包含消息ID、队列信息等发送结果的详细对象
     * @throws RuntimeException 当RocketMQ客户端抛出异常时
     */
    public <T> SendResult sendSync(String topic, String tag, T message) {
        String destination = destination(topic, tag);
        Message<T> springMessage = buildMessage(message, null);

        long startTime = System.currentTimeMillis();
        try {
            SendResult result = rocketMQTemplate.syncSend(destination, springMessage);
            long cost = System.currentTimeMillis() - startTime;
            log.info("同步发送消息成功, topic: {}, tag: {}, cost: {}ms, result: {}", topic, tag, cost, result);
            return result;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            log.error("同步发送消息失败, topic: {}, tag: {}, cost: {}ms", topic, tag, cost, e);
            throw e;
        }
    }

    /**
     * 异步发送消息
     *
     * <p>以异步方式发送消息，不阻塞调用线程，发送结果通过回调函数通知。
     * 适用于对延迟敏感、对可靠性要求较高的场景。
     *
     * @param <T>      消息体类型
     * @param topic    消息主题，不能为空
     * @param tag      消息标签，用于消息过滤和分类
     * @param message  消息体对象
     * @param callback 异步发送结果回调，onSuccess接收SendResult，onException接收Throwable
     */
    public <T> void sendAsync(String topic, String tag, T message, SendCallback callback) {
        String destination = destination(topic, tag);
        Message<T> springMessage = buildMessage(message, null);

        rocketMQTemplate.asyncSend(destination, springMessage, callback);
        log.info("异步发送消息, topic: {}, tag: {}", topic, tag);
    }

    /**
     * 单向发送消息
     *
     * <p>只管发送不等待结果，不关心是否发送成功。
     * 适用于可靠性要求不高、追求吞吐量的场景（如日志上报）。
     *
     * @param <T>     消息体类型
     * @param topic   消息主题，不能为空
     * @param tag     消息标签
     * @param message 消息体对象
     */
    public <T> void sendOneWay(String topic, String tag, T message) {
        String destination = destination(topic, tag);
        Message<T> springMessage = buildMessage(message, null);

        rocketMQTemplate.sendOneWay(destination, springMessage);
        log.info("单向发送消息, topic: {}, tag: {}", topic, tag);
    }

    /**
     * 发送延迟消息
     *
     * <p>以同步方式发送延迟消息，消息将在指定的延迟等级后投递给消费者。
     * 发送过程带异常捕获，RocketMQ客户端异常会记录错误日志并重新抛出。
     *
     * <h3>延迟等级说明</h3>
     * <p>RocketMQ支持的延迟等级：1s/5s/10s/30s/1m/2m/3m/4m/5m/6m/7m/8m/9m/10m/20m/30m/1h/2h</p>
     *
     * @param <T>        消息体类型
     * @param topic      消息主题，不能为空
     * @param tag        消息标签
     * @param message    消息体对象
     * @param delayLevel 延迟等级（1-18），参见RocketMQ延迟等级定义
     * @return SendResult 包含消息ID等发送结果详情
     * @throws RuntimeException 当RocketMQ客户端抛出异常时
     */
    public <T> SendResult sendDelay(String topic, String tag, T message, int delayLevel) {
        String destination = destination(topic, tag);
        Message<T> springMessage = buildMessage(message, null);

        long startTime = System.currentTimeMillis();
        try {
            SendResult result = rocketMQTemplate.syncSend(destination, springMessage, SYNC_SEND_TIMEOUT_MS, delayLevel);
            long cost = System.currentTimeMillis() - startTime;
            log.info("发送延迟消息成功, topic: {}, tag: {}, delayLevel: {}, cost: {}ms", topic, tag, delayLevel, cost);
            return result;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            log.error("发送延迟消息失败, topic: {}, tag: {}, delayLevel: {}, cost: {}ms", topic, tag, delayLevel, cost, e);
            throw e;
        }
    }

    /**
     * 发送带自定义Header的消息
     *
     * <p>以同步方式发送消息，附带自定义Header键值对。
     * Header信息可用于消息过滤、路由和消费端业务逻辑判断。
     * 发送过程带异常捕获，RocketMQ客户端异常会记录错误日志并重新抛出。
     *
     * @param <T>     消息体类型
     * @param topic   消息主题，不能为空
     * @param tag     消息标签
     * @param message 消息体对象
     * @param headers 自定义Header键值对，可为null或空Map
     * @return SendResult 包含消息ID等发送结果详情
     * @throws RuntimeException 当RocketMQ客户端抛出异常时
     */
    public <T> SendResult sendWithHeaders(String topic, String tag, T message, java.util.Map<String, Object> headers) {
        String destination = destination(topic, tag);
        Message<T> springMessage = buildMessage(message, headers);

        long startTime = System.currentTimeMillis();
        try {
            SendResult result = rocketMQTemplate.syncSend(destination, springMessage);
            long cost = System.currentTimeMillis() - startTime;
            log.info("发送带头部的消息成功, topic: {}, tag: {}, cost: {}ms", topic, tag, cost);
            return result;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            log.error("发送带头部的消息失败, topic: {}, tag: {}, cost: {}ms", topic, tag, cost, e);
            throw e;
        }
    }

    /**
     * 发送同业务键的顺序消息。
     *
     * @param <T> 消息体类型
     * @param topic 消息主题
     * @param tag 消息标签
     * @param message 消息体
     * @param hashKey 顺序键，同一键路由到同一消息队列
     * @return 发送结果
     */
    public <T> SendResult sendOrderly(String topic, String tag, T message, String hashKey) {
        if (hashKey == null || hashKey.isBlank()) {
            throw new IllegalArgumentException("顺序消息hashKey不能为空");
        }
        return rocketMQTemplate.syncSendOrderly(
                destination(topic, tag), buildMessage(message, null), hashKey, SYNC_SEND_TIMEOUT_MS);
    }

    /**
     * 发送事务消息。
     *
     * @param <T> 消息体类型
     * @param topic 消息主题
     * @param tag 消息标签
     * @param message 消息体
     * @param argument 本地事务参数
     * @return 事务发送结果
     */
    public <T> TransactionSendResult sendTransaction(String topic, String tag, T message, Object argument) {
        Message<T> springMessage = buildMessage(message, null);
        return rocketMQTemplate.sendMessageInTransaction(destination(topic, tag), springMessage, argument);
    }

    /**
     * 创建事务提交状态。
     *
     * @return 提交状态
     */
    public RocketMQLocalTransactionState commitTransaction() {
        return RocketMQLocalTransactionState.COMMIT;
    }

    /**
     * 同步发送消息并写入发件箱（Outbox 模式）
     *
     * <p>先将消息以 PENDING 状态持久化到发件箱，再尝试同步投递；投递成功后标记
     * DELIVERED，投递失败时保留 PENDING 记录并重新抛出异常，由发件箱调度器后续
     * 重试。本方法为阻塞 API，仅供同步调用边界使用；追加、发送和状态更新不是同一事务，
     * 发送成功而状态更新失败时可能再次投递，消费方仍须保证幂等。
     *
     * @param topic   消息主题，不能为空
     * @param tag     消息标签，用于消息过滤和分类
     * @param message 消息体对象（JSON 序列化后落箱）
     * @return SendResult 同步发送结果
     */
    public SendResult sendSyncWithOutbox(String topic, String tag, Map<String, Object> message) {
        MessageOutboxEntity outbox = buildOutboxEntity(topic, tag, message);
        messageOutboxService.append(outbox).block(Duration.ofSeconds(SYNC_SEND_TIMEOUT_MS));
        try {
            SendResult result = sendSync(topic, tag, message);
            messageOutboxService.markAsDelivered(outbox.getMessageId()).block(Duration.ofSeconds(SYNC_SEND_TIMEOUT_MS));
            return result;
        } catch (RuntimeException e) {
            log.error("消息发送失败，发件箱记录保留 PENDING 状态待调度重试: messageId={}, topic: {}", outbox.getMessageId(), topic, e);
            throw e;
        }
    }

    /**
     * 异步发送消息，失败时兜底写入发件箱（Outbox 模式）
     *
     * <p>优先异步投递；收到发送失败回调或发送过程抛出异常时，
     * 发起 PENDING 消息的异步发件箱写入，由调度器后续重试。本方法不等待兜底写入完成，
     * 写入失败只记录日志，不向调用方反馈，不能据此认定消息已经持久化。
     *
     * @param topic   消息主题，不能为空
     * @param tag     消息标签，用于消息过滤和分类
     * @param message 消息体对象（JSON 序列化后落箱）
     */
    public void sendAsyncWithFallback(String topic, String tag, Map<String, Object> message) {
        MessageOutboxEntity outbox = buildOutboxEntity(topic, tag, message);
        try {
            sendAsync(topic, tag, message, new SendCallback() {
                /**
                 * 在发送回调线程记录成功，不写入发件箱。
                 *
                 * @param sendResult RocketMQ 返回的发送结果，本回调不读取其内容
                 */
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.debug("异步发送消息成功, messageId: {}, topic: {}", outbox.getMessageId(), topic);
                }

                /**
                 * 在发送失败回调中发起异步落箱，不等待数据库写入完成。
                 *
                 * @param e RocketMQ 报告的发送异常，交由兜底流程记录
                 */
                @Override
                public void onException(Throwable e) {
                    fallbackToOutbox(outbox, e);
                }
            });
        } catch (RuntimeException e) {
            fallbackToOutbox(outbox, e);
        }
    }

    /**
     * 构建发件箱消息实体，投递状态与重试参数等默认值由 {@link MessageOutboxService#append} 补齐。
     */
    private MessageOutboxEntity buildOutboxEntity(String topic, String tag, Map<String, Object> message) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.warn("消息体 JSON 序列化失败，回退为 toString 形式落箱: topic: {}", topic, e);
            payload = String.valueOf(message);
        }
        return MessageOutboxEntity.builder()
                .messageId(UUID.randomUUID().toString())
                .topic(topic)
                .tag(tag)
                .payload(payload)
                .messageType("JSON")
                .build();
    }

    /**
     * 发送失败兜底：将消息落入发件箱（PENDING），由发件箱调度器重试投递。
     */
    private void fallbackToOutbox(MessageOutboxEntity outbox, Throwable cause) {
        log.error("消息发送失败，已落入发件箱待重试: messageId={}, topic: {}", outbox.getMessageId(), outbox.getTopic(), cause);
        messageOutboxService
                .append(outbox)
                .subscribe(saved -> {}, err -> log.error("发件箱落库失败: messageId={}", outbox.getMessageId(), err));
    }

    /**
     * 校验主题与标签并构建 RocketMQ 发送目标，不修剪输入中的首尾空白。
     *
     * @param topic 消息主题，不可为 {@code null} 或空白字符串
     * @param tag 消息标签，不可为 {@code null} 或空白字符串
     * @return 以冒号连接的 {@code topic:tag}
     * @throws IllegalArgumentException 当主题或标签为空或空白字符串时抛出
     */
    private String destination(String topic, String tag) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("消息topic不能为空");
        }
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("消息tag不能为空");
        }
        return topic + ":" + tag;
    }

    /**
     * 构建带统一消息标识和租户上下文的 Spring 消息，不执行发送或网络访问。
     *
     * <p>先应用自定义 Header；消息标识缺失、为字符串 {@code "null"} 或为空白时生成 UUID，
     * 再将标识同时写入 {@code apex-message-id} 与 {@code KEYS}，覆盖同名自定义值。
     * 存在可信租户上下文时覆盖 {@code tenantId}；上下文缺失时保留已有租户 Header。
     *
     * @param <T> 消息载荷类型
     * @param message 非空消息载荷
     * @param headers 自定义 Header，可为 {@code null}
     * @return 携带统一 Header 的消息
     * @throws IllegalArgumentException 当载荷为 {@code null} 时由消息构建器抛出
     */
    private <T> Message<T> buildMessage(T message, Map<String, Object> headers) {
        MessageBuilder<T> builder = MessageBuilder.withPayload(message);
        if (headers != null) {
            headers.forEach(builder::setHeader);
        }
        String messageId = headers == null ? null : String.valueOf(headers.get(MESSAGE_ID_HEADER));
        if (messageId == null || NULL_STRING.equals(messageId) || messageId.isBlank()) {
            messageId = UUID.randomUUID().toString();
        }
        builder.setHeader(MESSAGE_ID_HEADER, messageId);
        builder.setHeader(ROCKETMQ_KEYS_HEADER, messageId);
        TenantContext.currentTenantId().ifPresent(tenantId -> builder.setHeader(TENANT_ID_HEADER, tenantId));
        return builder.build();
    }
}
