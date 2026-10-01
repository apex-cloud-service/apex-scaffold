package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.OffMessageAssembler;
import com.apex.scaffold.application.dto.MessageDTO;
import com.apex.scaffold.application.dto.SendMessageRequest;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.model.entity.OffMessageEntity;
import com.apex.scaffold.domain.model.entity.OffMessageRecipientEntity;
import com.apex.scaffold.domain.repository.OffMessageRecipientRepository;
import com.apex.scaffold.domain.repository.OffMessageRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 办公消息应用服务
 *
 * <p>提供站内消息发送、收件箱管理、发件箱管理、消息状态跟踪等核心功能。
 * 支持多租户隔离、消息已读未读状态管理、消息详情查询等业务场景。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>消息发送：支持向单个或多个收件人发送消息</li>
 *   <li>收件箱管理：查询收到的消息列表，支持分页</li>
 *   <li>发件箱管理：查询已发送的消息列表，支持分页</li>
 *   <li>消息状态：标记消息为已读，批量标记所有消息已读</li>
 *   <li>未读统计：统计用户未读消息数量</li>
 *   <li>消息详情：查询单条消息的完整信息</li>
 * </ul>
 *
 * <h2>业务规则</h2>
 * <ul>
 *   <li>每条消息生成唯一的消息编码（MSG-时间戳-随机码）</li>
 *   <li>发送消息时自动记录发送人ID和发送时间</li>
 *   <li>支持一对多发送，自动创建收件人关联记录</li>
 *   <li>所有操作严格遵守租户隔离原则</li>
 * </ul>
 *
 * <h2>技术实现</h2>
 * <ul>
 *   <li>使用 Reactor 响应式编程模型，返回 Mono/Flux</li>
 *   <li>通过 Assembler 实现 DTO 与 Entity 之间的转换</li>
 *   <li>消息和收件人数据分别存储，通过关联查询获取完整信息</li>
 *   <li>租户ID从 TenantContext 获取，自动注入实体</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see OffMessageRepository
 * @see OffMessageRecipientRepository
 * @see OffMessageAssembler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OffMessageService {

    private final OffMessageRepository messageRepository;
    private final OffMessageRecipientRepository recipientRepository;
    private final OffMessageAssembler assembler;

    /**
     * 发送消息
     *
     * <p>生成唯一消息编码（MSG-时间戳-随机码），记录发送人、发送时间与状态（SENT），
     * 注入当前租户 ID，并为每个收件人创建未读的收件人关联记录。
     *
     * @param request 发送消息请求（含主题、内容、消息类型、优先级、收件人 ID 列表、过期时间与模板 ID）
     * @param senderId 发送人用户 ID
     * @return 发送后的消息 DTO
     */
    public Mono<MessageDTO> sendMessage(SendMessageRequest request, Long senderId) {
        return Mono.just(request)
                .flatMap(req -> {
                    OffMessageEntity entity = assembler.toEntity(req);
                    entity.setMessageCode(generateMessageCode());
                    entity.setSenderId(senderId);
                    entity.setSendTime(LocalDateTime.now());
                    entity.setStatus("SENT");
                    entity.setTenantId(TenantContext.currentTenantId().orElse(null));

                    return messageRepository
                            .insert(entity)
                            .flatMap(count -> messageRepository.findById(entity.getId()))
                            .flatMap(message -> saveRecipients(message, request.getRecipientIds())
                                    .thenReturn(message));
                })
                .map(assembler::toDTO);
    }

    /**
     * 分页查询收件箱
     *
     * <p>查询指定收件人收到的消息列表。
     *
     * @param recipientId 收件人用户 ID
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 收件消息 DTO 流
     */
    public Flux<MessageDTO> getInbox(Long recipientId, Integer pageNum, Integer pageSize) {
        return messageRepository
                .findByRecipientId(recipientId, pageNum, pageSize)
                .map(assembler::toDTO);
    }

    /**
     * 分页查询发件箱
     *
     * <p>查询指定发送人已发送的消息列表。
     *
     * @param senderId 发送人用户 ID
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 已发送消息 DTO 流
     */
    public Flux<MessageDTO> getSentMessages(Long senderId, Integer pageNum, Integer pageSize) {
        return messageRepository.findBySenderId(senderId, pageNum, pageSize).map(assembler::toDTO);
    }

    /**
     * 标记单条消息为已读
     *
     * @param messageId 消息 ID
     * @param recipientId 收件人用户 ID
     * @return 完成信号
     */
    public Mono<Void> markAsRead(Long messageId, Long recipientId) {
        return recipientRepository.markAsRead(messageId, recipientId).then();
    }

    /**
     * 标记指定收件人的所有消息为已读
     *
     * @param recipientId 收件人用户 ID
     * @return 完成信号
     */
    public Mono<Void> markAllAsRead(Long recipientId) {
        return recipientRepository.markAllAsRead(recipientId).then();
    }

    /**
     * 统计指定收件人的未读消息数量
     *
     * @param recipientId 收件人用户 ID
     * @return 未读消息数量
     */
    public Mono<Long> countUnread(Long recipientId) {
        return recipientRepository.countUnreadByRecipientId(recipientId);
    }

    /**
     * 查询消息详情
     *
     * @param messageId 消息 ID
     * @return 消息 DTO；消息不存在时 Mono 为空
     */
    public Mono<MessageDTO> getMessageDetail(Long messageId) {
        return messageRepository.findById(messageId).map(assembler::toDTO);
    }

    private Mono<Void> saveRecipients(OffMessageEntity message, List<Long> recipientIds) {
        if (recipientIds == null || recipientIds.isEmpty()) {
            return Mono.empty();
        }

        List<OffMessageRecipientEntity> recipients = new ArrayList<>(16);
        for (Long recipientId : recipientIds) {
            OffMessageRecipientEntity recipient = new OffMessageRecipientEntity();
            recipient.setMessageId(message.getId());
            recipient.setRecipientId(recipientId);
            recipient.setRecipientType("USER");
            recipient.setReadStatus(false);
            recipient.setTenantId(TenantContext.currentTenantId().orElse(null));
            recipients.add(recipient);
        }

        return recipientRepository.batchInsert(recipients).then();
    }

    private String generateMessageCode() {
        return "MSG-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
