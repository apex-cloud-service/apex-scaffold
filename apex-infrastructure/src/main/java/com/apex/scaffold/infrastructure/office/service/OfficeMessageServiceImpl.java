package com.apex.scaffold.infrastructure.office.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.office.message.OfficeMessage;
import com.apex.scaffold.domain.office.message.OfficeMessageRepository;
import com.apex.scaffold.domain.office.message.OfficeMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 办公平台消息领域服务实现
 *
 * <p>实现消息发送的核心业务逻辑，协调消息客户端和仓储完成消息发送。
 * 当前为最小化实现，待完善平台客户端集成。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeMessageServiceImpl implements OfficeMessageService {

    private final OfficeMessageRepository messageRepository;

    @Override
    public Mono<OfficeMessage> sendMessage(OfficeMessage message) {
        log.info("发送消息，平台：{}，类型：{}", message.getPlatform(), message.getMessageType());

        if (!message.canSend()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_INVALID, "消息参数不完整或状态不允许发送"));
        }

        // 当前最小化实现：直接保存消息记录
        // TODO: 集成平台消息客户端，调用外部平台API发送消息
        return messageRepository
                .save(message)
                .doOnSuccess(m -> log.info("消息保存成功，ID：{}", m.getId()))
                .doOnError(e -> log.error("消息发送失败", e));
    }

    @Override
    public Mono<OfficeMessage> sendBatchMessage(OfficeMessage message) {
        log.info("批量发送消息，平台：{}", message.getPlatform());

        if (!message.canSend()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_INVALID, "消息参数不完整或状态不允许发送"));
        }

        // TODO: 集成平台批量消息发送
        return messageRepository
                .save(message)
                .doOnSuccess(m -> log.info("批量消息保存成功，ID：{}", m.getId()))
                .doOnError(e -> log.error("批量消息发送失败", e));
    }

    @Override
    public Mono<Boolean> recallMessage(Long messageId) {
        return recallMessage(messageId, null);
    }

    /**
     * 撤回消息（内部方法，支持撤回原因）
     *
     * @param messageId 消息 ID
     * @param reason 撤回原因
     * @return 是否成功撤回
     */
    public Mono<Boolean> recallMessage(Long messageId, String reason) {
        log.info("撤回消息，消息ID：{}", messageId);

        return messageRepository
                .findById(messageId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "消息不存在")))
                .flatMap(message -> {
                    // TODO: 调用平台API撤回消息
                    log.warn("消息撤回功能待实现，消息ID：{}", messageId);
                    return Mono.just(true);
                })
                .doOnSuccess(result -> log.info("消息撤回完成，消息ID：{}", messageId))
                .doOnError(e -> log.error("消息撤回失败，消息ID：{}", messageId, e));
    }

    @Override
    public Mono<OfficeMessage> retryMessage(Long messageId) {
        log.info("重试发送消息，消息ID：{}", messageId);

        return messageRepository
                .findById(messageId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "消息不存在")))
                .flatMap(message -> {
                    if (!"FAILED".equals(message.getSendStatus())) {
                        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "只能重试发送失败的消息"));
                    }

                    // TODO: 检查重试次数限制，调用平台API重新发送
                    log.warn("消息重试功能待实现，消息ID：{}", messageId);
                    return messageRepository.save(message);
                })
                .doOnSuccess(m -> log.info("消息重试完成，消息ID：{}", messageId))
                .doOnError(e -> log.error("消息重试失败，消息ID：{}", messageId, e));
    }

    @Override
    public Mono<OfficeMessage> getMessageById(Long messageId) {
        log.info("查询消息，消息ID：{}", messageId);

        return messageRepository
                .findById(messageId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "消息不存在")))
                .doOnSuccess(m -> log.info("消息查询成功，ID：", m.getId()))
                .doOnError(e -> log.error("消息查询失败，消息ID：{}", messageId, e));
    }
}
