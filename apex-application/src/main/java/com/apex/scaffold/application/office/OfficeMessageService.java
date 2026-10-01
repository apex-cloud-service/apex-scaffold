package com.apex.scaffold.application.office;

import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.domain.office.message.*;
import com.apex.scaffold.domain.office.port.MessagePort;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 办公平台消息发送应用服务
 *
 * <p>提供消息发送的业务编排，协调领域层和基础设施层完成消息发送流程。
 * 支持飞书、钉钉、企业微信三大平台的消息发送。
 *
 * <p>主要职责：
 * <ul>
 *   <li>协调消息创建和验证</li>
 *   <li>根据平台路由到对应的客户端</li>
 *   <li>处理消息发送结果和状态更新</li>
 *   <li>提供消息发送的事务边界</li>
 * </ul>
 *
 * <p>本服务依赖 {@link MessagePort} 端口接口而非具体实现，
 * 符合依赖倒置原则和端口-适配器架构模式。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeMessageService {

    private static final String STATUS_FAILED = "FAILED";
    private static final int MAX_RETRY_COUNT = 3;

    private final OfficeMessageRepository messageRepository;
    private final MessagePort feishuMessageClient;
    private final MessagePort dingTalkMessageClient;
    private final MessagePort wecomMessageClient;

    /**
     * 发送消息
     *
     * <p>完整的消息发送流程：
     * <ol>
     *   <li>验证消息有效性</li>
     *   <li>保存消息记录（PENDING 状态）</li>
     *   <li>根据平台调用对应客户端发送</li>
     *   <li>更新消息状态（SUCCESS/FAILED）</li>
     * </ol>
     *
     * @param platform 办公平台
     * @param target 消息目标
     * @param content 消息内容
     * @return 消息ID
     */
    public Mono<Long> sendMessage(OfficePlatform platform, MessageTarget target, MessageContent content) {
        log.info("开始发送消息，平台：{}，目标类型：{}", platform, target.getTargetType());

        // 构建消息实体
        OfficeMessage message = OfficeMessage.builder()
                .platform(platform)
                .messageType(content.getType())
                .target(target)
                .content(content)
                .sendStatus("PENDING")
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        // 验证消息
        if (!message.canSend()) {
            return Mono.error(new IllegalArgumentException("消息验证失败，请检查必填字段"));
        }

        // 保存消息记录
        return messageRepository.save(message).flatMap(savedMessage -> {
            log.info("消息记录已保存，ID：{}", savedMessage.getId());

            // 根据平台发送消息
            return sendToPlatform(platform, target, content)
                    .flatMap(platformMessageId -> {
                        // 标记发送成功
                        savedMessage.markAsSent(platformMessageId);
                        return messageRepository.updateStatus(savedMessage);
                    })
                    .onErrorResume(error -> {
                        // 标记发送失败
                        log.error("消息发送失败，ID：{}，原因：{}", savedMessage.getId(), error.getMessage());
                        savedMessage.markAsFailed(error.getMessage());
                        return messageRepository.updateStatus(savedMessage);
                    })
                    .thenReturn(savedMessage.getId());
        });
    }

    /**
     * 根据平台路由到对应的客户端发送消息
     *
     * @param platform 办公平台
     * @param target 消息目标
     * @param content 消息内容
     * @return 平台消息ID
     */
    private Mono<String> sendToPlatform(OfficePlatform platform, MessageTarget target, MessageContent content) {
        return switch (platform) {
            case FEISHU -> feishuMessageClient.sendMessage(target, content);
            case DINGTALK -> dingTalkMessageClient.sendMessage(target, content);
            case WECOM -> wecomMessageClient.sendMessage(target, content);
            default -> Mono.error(new IllegalArgumentException("不支持的平台：" + platform));
        };
    }

    /**
     * 查询消息状态
     *
     * @param messageId 消息ID
     * @return 消息实体
     */
    public Mono<OfficeMessage> getMessageStatus(Long messageId) {
        log.info("查询消息状态，ID：{}", messageId);
        return messageRepository.findById(messageId);
    }

    /**
     * 重新发送失败的消息
     *
     * @param messageId 消息ID
     * @return 是否重新发送成功
     */
    public Mono<Boolean> resendMessage(Long messageId) {
        log.info("重新发送消息，ID：{}", messageId);

        return messageRepository.findById(messageId).flatMap(message -> {
            if (!STATUS_FAILED.equals(message.getSendStatus())) {
                return Mono.error(new IllegalStateException("只能重新发送失败的消息"));
            }
            if (message.getRetryCount() != null && message.getRetryCount() >= MAX_RETRY_COUNT) {
                return Mono.error(new IllegalStateException("消息重试次数已达上限"));
            }

            // 重置状态为 PENDING
            message.setSendStatus("PENDING");

            // 重新发送
            return sendToPlatform(message.getPlatform(), message.getTarget(), message.getContent())
                    .flatMap(platformMessageId -> {
                        message.markAsSent(platformMessageId);
                        return messageRepository.updateStatus(message).thenReturn(true);
                    })
                    .onErrorResume(error -> {
                        message.markAsFailed(error.getMessage());
                        return messageRepository.updateStatus(message).thenReturn(false);
                    });
        });
    }

    /**
     * 撤回消息
     *
     * <p>只能撤回发送成功且已获得平台消息ID的消息。撤回成功后消息状态更新为 RECALLED。
     *
     * @param messageId 消息ID
     * @return 是否撤回成功
     */
    public Mono<Boolean> recallMessage(Long messageId) {
        log.info("撤回消息，ID：{}", messageId);

        return messageRepository.findById(messageId).flatMap(message -> {
            if (!message.canRecall()) {
                return Mono.error(new IllegalStateException("消息状态不支持撤回"));
            }

            return recallOnPlatform(message.getPlatform(), message.getPlatformMessageId())
                    .flatMap(recalled -> {
                        message.markAsRecalled();
                        return messageRepository.updateStatus(message).thenReturn(recalled);
                    });
        });
    }

    /**
     * 根据平台路由到对应的客户端撤回消息
     *
     * @param platform 办公平台
     * @param platformMessageId 平台消息ID
     * @return 撤回是否成功
     */
    private Mono<Boolean> recallOnPlatform(OfficePlatform platform, String platformMessageId) {
        return switch (platform) {
            case FEISHU -> feishuMessageClient.recallMessage(platformMessageId);
            case DINGTALK -> dingTalkMessageClient.recallMessage(platformMessageId);
            case WECOM -> wecomMessageClient.recallMessage(platformMessageId);
            default -> Mono.error(new IllegalArgumentException("不支持的平台：" + platform));
        };
    }
}
