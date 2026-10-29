package com.apex.scaffold.office.off.application.service;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.off.application.assembler.OffMessageAssembler;
import com.apex.scaffold.office.off.application.dto.InboxCursorItemDTO;
import com.apex.scaffold.office.off.application.dto.MessageDTO;
import com.apex.scaffold.office.off.application.dto.MessageDeliveryDTO;
import com.apex.scaffold.office.off.application.dto.SendMessageRequest;
import com.apex.scaffold.office.off.domain.entity.OffMessageDeliveryEntity;
import com.apex.scaffold.office.off.domain.repository.MessageDeliveryRepository;
import com.apex.scaffold.office.off.domain.repository.OffMessageDeliveryRepository;
import com.apex.scaffold.office.off.domain.repository.OffMessageRecipientRepository;
import com.apex.scaffold.office.off.domain.repository.OffMessageRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 办公站内信应用服务，提交后发布在线提示，HTTP 提供持久消息补偿查询。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.0
 */
@Service
@RequiredArgsConstructor
public class OffMessageService {
    private final OffMessageRepository messageRepository;
    private final OffMessageRecipientRepository recipientRepository;
    private final OffMessageAssembler assembler;
    private final MessageDeliveryRepository deliveryRepository;
    private final OffMessageDeliveryRepository channelDeliveryRepository;
    private final ApplicationEventPublisher events;

    /**
     * 原子保存消息及接收人，仅在本地事务提交后发布提示事件。
     * @param request 经校验的请求
     * @param senderId 可信发送用户
     * @return 持久化消息
     */
    public Mono<MessageDTO> sendMessage(SendMessageRequest request, Long senderId) {
        return Mono.deferContextual(context -> {
            String tenant = context.get(TenantContext.REACTOR_CONTEXT_KEY);
            var entity = assembler.toEntity(request);
            entity.setMessageCode("MSG-" + UUID.randomUUID());
            entity.setSenderId(senderId);
            entity.setSendTime(LocalDateTime.now());
            entity.setStatus("SENT");
            if (entity.getMessageType() == null) {
                entity.setMessageType("NOTICE");
            }
            if (entity.getPriority() == null) {
                entity.setPriority("NORMAL");
            }
            entity.setTenantId(tenant);
            var recipients = request.getRecipientIds().stream().distinct().toList();
            return deliveryRepository
                    .save(entity, recipients)
                    .doOnNext(saved -> {
                        try {
                            events.publishEvent(new MessageCommittedEvent(tenant, saved.getId(), recipients));
                        } catch (RuntimeException ignored) {
                            // 通知失败不改变已提交业务结果，客户端通过 HTTP 补偿。
                        }
                    })
                    .map(assembler::toDTO);
        });
    }

    /**
     * 查询当前用户收件箱。
     * @param recipientId 可信用户
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @return 消息列表
     */
    public Flux<MessageDTO> getInbox(Long recipientId, Integer pageNum, Integer pageSize) {
        return TenantContext.withTenant(messageRepository
                .findByRecipientId(recipientId, Math.max(1, pageNum), Math.min(100, Math.max(1, pageSize)))
                .map(assembler::toDTO));
    }

    /**
     * 查询当前用户发件箱。
     * @param senderId 可信用户
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @return 消息列表
     */
    public Flux<MessageDTO> getSentMessages(Long senderId, Integer pageNum, Integer pageSize) {
        return TenantContext.withTenant(messageRepository
                .findBySenderId(senderId, Math.max(1, pageNum), Math.min(100, Math.max(1, pageSize)))
                .map(assembler::toDTO));
    }

    /**
     * 标记当前用户收到的消息已读。
     * @param messageId 消息标识
     * @param recipientId 可信用户
     * @return 完成信号
     */
    public Mono<Void> markAsRead(Long messageId, Long recipientId) {
        return TenantContext.withTenant(recipientRepository.markAsRead(messageId, recipientId))
                .then();
    }

    /**
     * 标记当前用户全部消息已读。
     * @param recipientId 可信用户
     * @return 完成信号
     */
    public Mono<Void> markAllAsRead(Long recipientId) {
        return TenantContext.withTenant(recipientRepository.markAllAsRead(recipientId))
                .then();
    }

    /**
     * 统计当前用户未读消息。
     * @param recipientId 可信用户
     * @return 未读数量
     */
    public Mono<Long> countUnread(Long recipientId) {
        return TenantContext.withTenant(recipientRepository.countUnreadByRecipientId(recipientId));
    }

    /**
     * 校验同租户的发件人或接收人归属后返回详情。
     * @param messageId 消息标识
     * @param userId 可信用户
     * @return 可见消息，不存在或无权限返回空
     */
    public Mono<MessageDTO> getMessageDetail(Long messageId, Long userId) {
        return Mono.deferContextual(context -> TenantContext.withTenant(messageRepository.findById(messageId))
                .filter(message ->
                        Objects.equals(message.getTenantId(), context.get(TenantContext.REACTOR_CONTEXT_KEY)))
                .flatMap(message -> Objects.equals(message.getSenderId(), userId)
                        ? Mono.just(message)
                        : TenantContext.withTenant(recipientRepository.findByMessageIdAndRecipientId(messageId, userId))
                                .filter(recipient -> Objects.equals(recipient.getTenantId(), message.getTenantId()))
                                .map(recipient -> message))
                .map(assembler::toDTO));
    }

    /**
     * 按增量游标查询当前用户收件箱，返回按接收记录 id 升序的条目。
     * @param recipientId 可信用户
     * @param afterId 游标，仅返回接收记录 id 大于该值的条目，首次传 0
     * @param limit 返回条数上限，最大 100
     * @return 收件箱条目，{@code recipientRecordId} 作为下一页游标
     */
    public Flux<InboxCursorItemDTO> getInboxCursor(Long recipientId, Long afterId, Integer limit) {
        long cursor = afterId == null ? 0L : Math.max(0L, afterId);
        int size = limit == null ? 20 : Math.min(100, Math.max(1, limit));
        return TenantContext.withTenant(recipientRepository
                .findInboxAfterId(recipientId, cursor, size)
                .flatMap(recipient -> TenantContext.withTenant(messageRepository.findById(recipient.getMessageId()))
                        .map(message -> InboxCursorItemDTO.builder()
                                .recipientRecordId(recipient.getId())
                                .messageId(message.getId())
                                .messageCode(message.getMessageCode())
                                .subject(message.getSubject())
                                .content(message.getContent())
                                .messageType(message.getMessageType())
                                .priority(message.getPriority())
                                .senderId(message.getSenderId())
                                .senderName(message.getSenderName())
                                .sendTime(message.getSendTime())
                                .readStatus(recipient.getReadStatus())
                                .readTime(recipient.getReadTime())
                                .build())));
    }

    /**
     * 查询消息的渠道投递记录：发送人可见全部接收人的投递，接收人仅可见本人的投递。
     * @param messageId 消息标识
     * @param userId 可信用户
     * @return 可见投递记录，消息不存在或无权限返回空
     */
    public Flux<MessageDeliveryDTO> getMessageDeliveries(Long messageId, Long userId) {
        return Flux.deferContextual(context -> TenantContext.withTenant(messageRepository.findById(messageId))
                .filter(message ->
                        Objects.equals(message.getTenantId(), context.get(TenantContext.REACTOR_CONTEXT_KEY)))
                .flatMapMany(message -> Objects.equals(message.getSenderId(), userId)
                        ? TenantContext.withTenant(channelDeliveryRepository.findByMessageId(messageId))
                        : TenantContext.withTenant(
                                channelDeliveryRepository.findByMessageIdAndRecipientId(messageId, userId)))
                .map(this::toDeliveryDTO));
    }

    /**
     * 记录渠道投递结果，渠道与状态仅接受白名单取值；投递成功时写入送达时间。
     * @param messageId 消息标识
     * @param recipientId 接收人标识
     * @param channel 投递渠道（INBOX/FEISHU/DINGTALK/WECOM）
     * @param status 投递状态（PENDING/SUCCESS/FAILED/UNKNOWN）
     * @param platformMessageId 第三方平台消息标识，可为空
     * @param failureReason 失败原因，可为空
     * @return 持久化的投递记录
     */
    public Mono<MessageDeliveryDTO> recordDelivery(
            Long messageId,
            Long recipientId,
            String channel,
            String status,
            String platformMessageId,
            String failureReason) {
        if (!OffMessageDeliveryEntity.isSupportedChannel(channel)) {
            return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "不支持的投递渠道：" + channel));
        }
        if (!OffMessageDeliveryEntity.isSupportedStatus(status)) {
            return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "不支持的投递状态：" + status));
        }
        return Mono.deferContextual(context -> {
            String tenant = context.get(TenantContext.REACTOR_CONTEXT_KEY);
            return TenantContext.withTenant(recipientRepository.findByMessageIdAndRecipientId(messageId, recipientId))
                    .filter(recipient -> Objects.equals(recipient.getTenantId(), tenant))
                    .switchIfEmpty(Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "消息接收记录不存在")))
                    .flatMap(recipient -> {
                        OffMessageDeliveryEntity entity = new OffMessageDeliveryEntity();
                        entity.setMessageId(messageId);
                        entity.setRecipientId(recipientId);
                        entity.setChannel(channel);
                        entity.setDeliveryStatus(status);
                        entity.setPlatformMessageId(platformMessageId);
                        entity.setFailureReason(failureReason);
                        if (OffMessageDeliveryEntity.STATUS_SUCCESS.equals(status)) {
                            entity.setDeliveredAt(LocalDateTime.now());
                        }
                        entity.setTenantId(tenant);
                        return TenantContext.withTenant(channelDeliveryRepository.upsert(entity));
                    })
                    .map(this::toDeliveryDTO);
        });
    }

    private MessageDeliveryDTO toDeliveryDTO(OffMessageDeliveryEntity entity) {
        return MessageDeliveryDTO.builder()
                .id(entity.getId())
                .messageId(entity.getMessageId())
                .recipientId(entity.getRecipientId())
                .channel(entity.getChannel())
                .deliveryStatus(entity.getDeliveryStatus())
                .platformMessageId(entity.getPlatformMessageId())
                .failureReason(entity.getFailureReason())
                .deliveredAt(entity.getDeliveredAt())
                .build();
    }
}
