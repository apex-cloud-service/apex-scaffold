package com.apex.scaffold.infrastructure.notification;

import com.apex.scaffold.common.context.TenantContext;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * 按租户与账户隔离的站内通知推送服务。
 *
 * <p>基于 Reactor {@link Sinks} 实现进程内发布订阅。发布方必须提供租户范围与账户 ID，
 * 订阅方必须携带可信 Reactor 租户上下文，不支持无接收人广播。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@Slf4j
@Service
public class NotificationService {

    private final Sinks.Many<NotificationMessage> sink =
            Sinks.many().multicast().onBackpressureBuffer(100);

    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeConnections = new AtomicInteger();

    /**
     * 构造通知推送服务并注册 SSE 连接数指标。
     *
     * @param meterRegistry Micrometer 指标注册表
     */
    public NotificationService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("apex.sse.connections", activeConnections, AtomicInteger::get)
                .description("SSE 实时通知活动连接数")
                .register(meterRegistry);
    }

    /**
     * 在当前可信同步租户上下文中发布账户通知。
     *
     * @param recipient 接收账户 ID 的字符串表示
     * @param content 消息内容
     */
    public void publish(String recipient, String content) {
        publish(new NotificationMessage(recipient, content));
    }

    /**
     * 发布带租户范围的通知。
     *
     * @param message 含租户及接收账户的消息
     */
    public void publish(NotificationMessage message) {
        publishLocal(message);
    }

    /**
     * 仅向本实例订阅者扇出，不再次发布到 MQ。
     *
     * @param message 含租户及接收账户的消息
     */
    public void publishLocal(NotificationMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("通知消息不能为空");
        }
        requireScope(message.tenantId(), "租户");
        requireScope(message.recipient(), "接收账户");
        meterRegistry.counter("apex.sse.events.count").increment();
        Sinks.EmitResult result = sink.tryEmitNext(message);
        if (result.isFailure()) {
            log.warn("通知消息发布失败: result={}", result);
        }
    }

    /**
     * 从可信 Reactor 上下文捕获租户，订阅该租户的指定账户通知。
     *
     * @param recipient 接收账户 ID 的字符串表示
     * @return 按租户及账户过滤的通知流
     */
    public Flux<NotificationMessage> stream(String recipient) {
        return Flux.deferContextual(context -> {
            String tenantId = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            requireScope(tenantId, "租户");
            requireScope(recipient, "接收账户");
            return sink.asFlux()
                    .filter(msg -> tenantId.equals(msg.tenantId()) && recipient.equals(msg.recipient()))
                    .doOnSubscribe(subscription -> activeConnections.incrementAndGet())
                    .doFinally(signal -> activeConnections.decrementAndGet())
                    .doOnError(e -> log.warn("通知流异常: {}", e.getMessage()));
        });
    }

    /**
     * 拒绝缺失范围，避免将空接收人解释为广播。
     *
     * @param value 范围值
     * @param name 范围名称
     */
    private static void requireScope(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("通知" + name + "不能为空");
        }
    }

    /**
     * 带显式租户及账户范围的通知消息体。
     *
     * @param tenantId 可信租户 ID
     * @param recipient 接收账户 ID 的字符串表示，不接受 OpenID 别名
     * @param content 消息内容
     */
    public record NotificationMessage(String tenantId, String recipient, String content) {
        /**
         * 从同步调用方的可信租户上下文捕获范围。
         *
         * @param recipient 接收账户 ID
         * @param content 消息内容
         */
        public NotificationMessage(String recipient, String content) {
            this(TenantContext.currentTenantId().orElse(null), recipient, content);
        }
    }
}
