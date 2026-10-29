package com.apex.scaffold.infrastructure.outbox.dispatcher;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.infrastructure.mq.service.MqProducerService;
import com.apex.scaffold.infrastructure.outbox.entity.MessageOutboxEntity;
import com.apex.scaffold.infrastructure.outbox.service.MessageOutboxService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 消息发件箱调度器
 *
 * <p>定时扫描本地消息表(Outbox),将待投递消息发送到RocketMQ。
 * 采用分布式锁机制,确保多实例部署时消息不被重复投递。
 *
 * <h2>调度机制</h2>
 * <ul>
 *   <li>每5秒执行一次调度任务(可通过配置调整)</li>
 *   <li>每次调度先释放过期锁,再批量获取待投递消息</li>
 *   <li>使用实例唯一标识(UUID)竞争消息锁</li>
 *   <li>锁定成功后发送消息到RocketMQ</li>
 *   <li>发送成功标记为DELIVERED,失败增加重试计数</li>
 * </ul>
 *
 * <h2>失败重试策略</h2>
 * <ul>
 *   <li>最大重试次数由max_attempts字段控制(默认5次)</li>
 *   <li>重试延迟采用指数退避: 10s、20s、40s、80s、160s</li>
 *   <li>超过最大重试次数后进入DEAD状态</li>
 *   <li>死信消息需人工介入或通过死信处理器补偿</li>
 * </ul>
 *
 * <h2>多实例协调</h2>
 * <p>在多实例部署环境下,通过数据库CAS操作实现分布式锁:
 * <ol>
 *   <li>所有实例同时扫描待投递消息</li>
 *   <li>每个实例尝试用自己的UUID锁定消息</li>
 *   <li>只有锁定成功的实例才能投递该消息</li>
 *   <li>其他实例锁定失败,跳过该消息</li>
 * </ol>
 *
 * <h2>配置说明</h2>
 * <pre>
 * apex:
 *   outbox:
 *     dispatcher:
 *       enabled: true              # 是否启用调度器
 *       fixed-delay-ms: 5000       # 调度间隔(毫秒)
 *       batch-size: 100            # 每次调度批量大小
 * </pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see MessageOutboxService
 * @see MqProducerService
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "apex.features", name = "message-queue", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "apex.outbox.dispatcher.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnBean(RocketMQTemplate.class)
public class MessageOutboxDispatcher {

    private final MessageOutboxService outboxService;

    private final MqProducerService mqProducerService;

    private final String instanceId = UUID.randomUUID().toString();

    /** 定时派发 Outbox 待投递消息（由 {@code @Scheduled} 按 fixedDelay 周期触发）。 */
    @Scheduled(fixedDelayString = "${apex.outbox.dispatcher.fixed-delay-ms:5000}")
    public void dispatch() {
        outboxService.releaseExpiredLocks().block();

        int batchSize = 100;
        outboxService
                .fetchPendingMessages(batchSize)
                .flatMap(this::tryDispatch)
                .doOnError(e -> log.error("Outbox调度异常", e))
                .onErrorResume(e -> Mono.empty())
                .blockLast();
    }

    /**
     * 尝试锁定并投递单条 Outbox 消息，未取得锁时直接跳过。
     *
     * <p>取得锁后将同步发送隔离到远程调用调度器执行，避免阻塞 JDBC 调度器线程。
     * 成功后标记已投递；发送失败一律调用 {@code markAsFailed} 登记：未达尝试上限时
     * 按指数退避恢复 PENDING 等待重试，达到上限时进入 DEAD。本方法不自行判断次数，
     * 重试语义由失败登记统一承担。响应式流程中的错误会记录日志并转换为空完成。
     *
     * @param entity 待投递消息及其当前重试状态
     * @return 投递流程的完成信号；正常完成也可能表示跳过或错误已被处理，不保证投递成功
     */
    private Mono<Void> tryDispatch(MessageOutboxEntity entity) {
        return outboxService
                .lockMessage(entity.getMessageId(), instanceId)
                .filter(Boolean::booleanValue)
                .flatMap(locked -> Mono.fromCallable(() ->
                                mqProducerService.sendSync(entity.getTopic(), entity.getTag(), entity.getPayload()))
                        .subscribeOn(BlockingIoSchedulers.remote())
                        .flatMap(result -> {
                            log.info("消息投递成功: messageId={}, msgId={}", entity.getMessageId(), result.getMsgId());
                            return outboxService.markAsDelivered(entity.getMessageId());
                        })
                        .onErrorResume(e -> {
                            log.error("消息投递失败,登记重试或死信: messageId={}", entity.getMessageId(), e);
                            return outboxService.markAsFailed(entity.getMessageId(), e.getMessage());
                        }))
                .onErrorResume(e -> {
                    log.error("消息投递流程异常: messageId={}", entity.getMessageId(), e);
                    return Mono.empty();
                });
    }
}
