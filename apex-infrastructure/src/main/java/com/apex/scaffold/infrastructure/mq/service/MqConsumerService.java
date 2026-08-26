package com.apex.scaffold.infrastructure.mq.service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * MQ消费者服务
 *
 * <p>提供消息消费的基础服务，包括分布式消息幂等性控制和处理结果封装。
 * 基于 Redisson（Redis）实现分布式幂等，适用于多实例部署环境。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>消息幂等性控制：基于 Redis 的原子操作防止重复消费相同的消息</li>
 *   <li>消息处理：提供统一的消息处理入口</li>
 *   <li>处理结果封装：封装消息处理的结果信息</li>
 * </ul>
 *
 * <h2>设计模式</h2>
 * <ul>
 *   <li><strong>幂等消费者模式</strong>：通过 Redis {@code RBucket.setIfAbsent} 原子操作
 *       记录已处理消息ID，确保消息不会被重复处理</li>
 *   <li><strong>模板方法模式</strong>：通过 {@link MessageHandler} 函数式接口定义消息处理逻辑骨架</li>
 *   <li><strong>结果封装模式</strong>：通过 {@link ProcessingResult} 统一封装处理状态和数据</li>
 * </ul>
 *
 * <h2>幂等性说明</h2>
 * <p>使用 Redis TTL 自动过期机制实现分布式幂等性：
 * <ol>
 *   <li>消息首次到达时，通过 {@code setIfAbsent} 原子操作尝试写入 Redis key</li>
 *   <li>写入成功表示首次处理，正常执行业务逻辑</li>
 *   <li>写入失败表示消息已被处理，直接返回重复结果</li>
 *   <li>Redis 在 TTL 到期后自动删除 key，防止内存无限增长</li>
 * </ol>
 *
 * <h2>Key 格式</h2>
 * <pre>
 *   mq:idempotent:{messageId}
 * </pre>
 *
 * @author Apex Team
 * @version 3.0.0
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(RocketMQTemplate.class)
public class MqConsumerService {
    private static final Logger log = LoggerFactory.getLogger(MqConsumerService.class);

    /** 消息幂等性缓存键前缀 */
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:";

    /** 默认幂等性记录 TTL（分钟） */
    private static final long DEFAULT_IDEMPOTENT_TTL_MINUTES = 30;

    /**
     * Redisson 客户端
     *
     * <p>通过构造器注入获取，用于与 Redis 交互实现分布式幂等。
     */
    private final RedissonClient redissonClient;

    /**
     * 幂等性记录 TTL（分钟）
     *
     * <p>可通过配置 {@code mq.consumer.idempotent-ttl-minutes} 自定义。
     */
    @Value("${mq.consumer.idempotent-ttl-minutes:30}")
    private long idempotentTtlMinutes;

    /**
     * 关闭标记
     */
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    /**
     * 处理消息（幂等）
     *
     * <p>根据消息ID进行分布式幂等性检查，防止重复消费。
     * 使用 Redis 的原子操作确保在分布式环境下的幂等性。
     *
     * <h3>幂等性保证</h3>
     * <ol>
     *   <li>通过 {@code RBucket.setIfAbsent} 原子性写入 Redis key</li>
     *   <li>写入成功：首次处理，正常执行业务逻辑，成功则保留 key，失败则删除 key</li>
     *   <li>写入失败：消息已处理，跳过处理</li>
     * </ol>
     *
     * @param <T>       消息类型
     * @param messageId 消息唯一标识
     * @param message   消息体
     * @param handler   消息处理器
     * @return true-处理成功或业务已处理，false-处理失败
     */
    public <T> boolean processMessage(String messageId, T message, MessageHandler<T> handler) {
        if (shuttingDown.get()) {
            log.warn("服务正在关闭，拒绝处理消息, messageId: {}", messageId);
            return false;
        }

        String key = IDEMPOTENT_KEY_PREFIX + messageId;
        log.debug("开始幂等性检查, messageId: {}", messageId);

        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        Duration ttl = getTtl();

        // 原子性尝试标记消息为已处理
        boolean firstTime = bucket.setIfAbsent("1", ttl);
        if (!firstTime) {
            log.warn("消息已处理, 跳过重复处理, messageId: {}", messageId);
            return true;
        }

        try {
            log.info("开始处理消息, messageId: {}", messageId);
            boolean result = handler.handle(message);

            if (result) {
                log.info("消息处理成功, messageId: {}", messageId);
            } else {
                // 处理失败时删除幂等标记，允许重试
                bucket.delete();
                log.warn("消息处理失败, 删除幂等标记允许重试, messageId: {}", messageId);
            }
            return result;
        } catch (Exception e) {
            // 异常时删除幂等标记，允许重试
            bucket.delete();
            log.error("消息处理异常, messageId: {}", messageId, e);
            return false;
        }
    }

    /**
     * 处理消息并返回详细结果（幂等）
     *
     * <p>根据消息ID进行分布式幂等性检查，并返回完整的处理结果信息。
     *
     * @param <T>       消息类型
     * @param messageId 消息唯一标识
     * @param message   消息体
     * @param handler   消息处理器
     * @return ProcessingResult 包含处理状态和数据的详细结果
     */
    public <T> ProcessingResult<T> processMessageWithResult(String messageId, T message, MessageHandler<T> handler) {
        if (shuttingDown.get()) {
            log.warn("服务正在关闭，拒绝处理消息, messageId: {}", messageId);
            return ProcessingResult.error(messageId, "服务正在关闭");
        }

        String key = IDEMPOTENT_KEY_PREFIX + messageId;
        log.debug("开始幂等性检查, messageId: {}", messageId);

        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        Duration ttl = getTtl();

        // 原子性尝试标记消息为已处理
        boolean firstTime = bucket.setIfAbsent("1", ttl);
        if (!firstTime) {
            log.warn("消息已处理, 跳过重复处理, messageId: {}", messageId);
            return ProcessingResult.duplicate(messageId);
        }

        try {
            log.info("开始处理消息, messageId: {}", messageId);
            boolean success = handler.handle(message);

            if (success) {
                log.info("消息处理成功, messageId: {}", messageId);
                return ProcessingResult.success(messageId, message);
            } else {
                // 处理失败时删除幂等标记，允许重试
                bucket.delete();
                log.warn("消息处理失败, 删除幂等标记允许重试, messageId: {}", messageId);
                return ProcessingResult.failed(messageId, "处理返回false");
            }
        } catch (Exception e) {
            // 异常时删除幂等标记，允许重试
            bucket.delete();
            log.error("消息处理异常, messageId: {}", messageId, e);
            return ProcessingResult.error(messageId, e.getMessage());
        }
    }

    /**
     * 获取幂等性记录 TTL
     *
     * <p>使用配置的 TTL 值，若配置值非法则使用默认值 30 分钟。
     *
     * @return TTL Duration
     */
    private Duration getTtl() {
        long minutes = idempotentTtlMinutes > 0 ? idempotentTtlMinutes : DEFAULT_IDEMPOTENT_TTL_MINUTES;
        return Duration.ofMinutes(minutes);
    }

    /**
     * 销毁资源
     *
     * <p>在 Spring 容器销毁 Bean 之前调用，设置关闭标记。
     * Redis 的 TTL 机制自动处理 key 过期，无需手动清理。
     */
    @PreDestroy
    /**
     * destroy
     */
    public void destroy() {
        log.info("MqConsumerService 开始关闭");
        shuttingDown.set(true);
        log.info("MqConsumerService 关闭完成");
    }

    /**
     * 消息处理器
     *
     * <p>函数式接口，用于定义消息处理逻辑。
     *
     * @param <T> 消息类型
     */
    @FunctionalInterface
    public interface MessageHandler<T> {
        /**
         * 处理消息
         *
         * @param message 待处理的消息
         * @return 处理结果，true 表示成功，false 表示失败
         */
        boolean handle(T message);
    }

    /**
     * 消息处理结果
     *
     * <p>封装消息处理的完整结果信息，包括处理状态、错误信息和处理后的数据。
     *
     * @param <T> 处理结果中携带的数据类型
     */
    @Getter
    public static class ProcessingResult<T> {
        /**
         * 消息 ID
         *
         * <p>唯一标识一条消息。
         */
        private final String messageId;

        /**
         * 处理是否成功
         *
         * <p>true 表示消息处理成功，false 表示处理失败。
         */
        private final boolean success;

        /**
         * 是否为重复消息
         *
         * <p>true 表示该消息已被处理过，属于重复消费。
         */
        private final boolean duplicate;

        /**
         * 错误信息
         *
         * <p>当处理失败时的详细错误描述。
         */
        private final String errorMessage;

        /**
         * 处理后的数据
         *
         * <p>消息处理完成后返回的结果数据。
         */
        private final T payload;

        /**
         * 构造处理结果
         *
         * @param messageId    消息 ID
         * @param success      处理是否成功
         * @param duplicate    是否为重复消息
         * @param errorMessage 错误信息
         * @param payload      处理后的数据
         */
        private ProcessingResult(String messageId, boolean success, boolean duplicate, String errorMessage, T payload) {
            this.messageId = messageId;
            this.success = success;
            this.duplicate = duplicate;
            this.errorMessage = errorMessage;
            this.payload = payload;
        }

        /**
         * 创建成功结果
         *
         * @param messageId 消息 ID
         * @param payload   处理后的数据
         * @param <T>       数据类型
         * @return 成功结果
         */
        public static <T> ProcessingResult<T> success(String messageId, T payload) {
            return new ProcessingResult<>(messageId, true, false, null, payload);
        }

        /**
         * 创建失败结果
         *
         * @param messageId 消息 ID
         * @param reason    失败原因
         * @param <T>       数据类型
         * @return 失败结果
         */
        public static <T> ProcessingResult<T> failed(String messageId, String reason) {
            return new ProcessingResult<>(messageId, false, false, reason, null);
        }

        /**
         * 创建异常结果
         *
         * @param messageId    消息 ID
         * @param errorMessage 异常信息
         * @param <T>          数据类型
         * @return 异常结果
         */
        public static <T> ProcessingResult<T> error(String messageId, String errorMessage) {
            return new ProcessingResult<>(messageId, false, false, errorMessage, null);
        }

        /**
         * 创建重复消息结果
         *
         * @param messageId 消息 ID
         * @param <T>       数据类型
         * @return 重复结果
         */
        public static <T> ProcessingResult<T> duplicate(String messageId) {
            return new ProcessingResult<>(messageId, true, true, null, null);
        }
    }
}
