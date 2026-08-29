package com.apex.scaffold.infrastructure.notification;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * 站内通知推送服务（C6 WebSocket/SSE 消息推送）。
 *
 * <p>基于 Reactor {@link Sinks} 实现进程内发布-订阅：业务方调用
 * {@link #publish(String)} 广播消息，客户端通过 SSE 端点订阅
 * {@link #stream(String)} 实时接收（按接收人过滤）。</p>
 *
 * <p><b>设计说明</b>：本实现为单实例进程内广播（{@code biz_system_message}
 * 表持久化 + 多实例横向扩展留待接入 RocketMQ 广播），满足脚手架演示
 * 与中小规模部署需求。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@Service
public class NotificationService {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** 消息广播流（多播，保留最近 100 条供迟到订阅者） */
    private final Sinks.Many<NotificationMessage> sink =
            Sinks.many().multicast().onBackpressureBuffer(100);

    /** 指标注册表。 */
    private final MeterRegistry meterRegistry;

    /** 活动 SSE 连接数（Grafana「异步响应 / 幂等重试」分组）。 */
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
     * 发布站内通知消息。
     *
     * @param recipient 接收人标识（用户 openId 或 userId）
     * @param content   消息内容
     */
    public void publish(String recipient, String content) {
        publish(new NotificationMessage(recipient, content));
    }

    /**
     * 发布站内通知消息（指定消息体）。
     *
     * @param message 消息体（含接收人）
     */
    public void publish(NotificationMessage message) {
        meterRegistry.counter("apex.sse.events.count").increment();
        Sinks.EmitResult result = sink.tryEmitNext(message);
        if (result.isFailure()) {
            log.warn("通知消息发布失败: result={}, content={}", result, message.content());
        }
    }

    /**
     * 订阅指定接收人的实时通知流（SSE 数据源）。
     *
     * <p>连接建立时递增活动连接数，取消/完成/异常时递减，供
     * {@code apex.sse.connections} 指标反映当前长连接规模。</p>
     *
     * @param recipient 接收人标识
     * @return 通知消息流（接收人不匹配的消息自动过滤）
     */
    public Flux<NotificationMessage> stream(String recipient) {
        return sink.asFlux()
                .filter(msg -> msg.recipient() == null || msg.recipient().equals(recipient))
                .doOnSubscribe(subscription -> activeConnections.incrementAndGet())
                .doFinally(signal -> activeConnections.decrementAndGet())
                .doOnError(e -> log.warn("通知流异常: {}", e.getMessage()));
    }

    /**
     * 站内通知消息体。
     *
     * @param recipient 接收人标识
     * @param content   消息内容
     */
    public record NotificationMessage(String recipient, String content) {}
}
