package com.apex.scaffold.infrastructure.mq.service;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class MqProducerService {
    private static final Logger log = LoggerFactory.getLogger(MqProducerService.class);

    /** 同步发送超时时间（毫秒） */
    private static final long SYNC_SEND_TIMEOUT_MS = 30000L;

    /**
     * RocketMQ 模板
     *
     * <p>用于发送 RocketMQ 消息，提供同步、异步、单向和事务等多种发送方式。
     */
    private final RocketMQTemplate rocketMQTemplate;

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
        String destination = topic + ":" + tag;
        Message<T> springMessage = MessageBuilder.withPayload(message).build();

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
        String destination = topic + ":" + tag;
        Message<T> springMessage = MessageBuilder.withPayload(message).build();

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
        String destination = topic + ":" + tag;
        Message<T> springMessage = MessageBuilder.withPayload(message).build();

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
        String destination = topic + ":" + tag;
        Message<T> springMessage = MessageBuilder.withPayload(message).build();

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
        String destination = topic + ":" + tag;
        MessageBuilder<T> builder = MessageBuilder.withPayload(message);

        if (headers != null && !headers.isEmpty()) {
            headers.forEach(builder::setHeader);
        }

        Message<T> springMessage = builder.build();

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
}
