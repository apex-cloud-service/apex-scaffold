package com.apex.scaffold.application.office.message;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.office.message.MessageStatus;
import com.apex.scaffold.domain.office.message.OfficeMessage;
import com.apex.scaffold.domain.office.message.OfficeMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 办公平台消息应用服务
 *
 * <p>提供消息发送的应用层服务，协调领域服务、基础设施服务完成消息发送业务流程。
 *
 * <p>主要功能：
 * <ul>
 *   <li>处理消息发送命令，编排业务逻辑</li>
 *   <li>管理消息发送的事务边界</li>
 *   <li>提供消息重试机制</li>
 *   <li>记录消息发送历史</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeMessageApplicationService {
    
    private final OfficeMessageService messageService;
    private final TransactionalOperator transactionalOperator;
    
    /**
     * 发送消息到办公平台
     *
     * <p>根据命令参数创建消息实体，调用领域服务发送消息，并保存发送记录。
     * 整个过程在事务中执行，确保数据一致性。
     *
     * @param command 发送消息命令
     * @return 发送后的消息实体
     */
    public Mono<OfficeMessage> sendMessage(SendMessageCommand command) {
        log.info("开始发送消息，平台：{}，接收者类型：{}，接收者数量：{}", 
                command.getPlatform(), command.getRecipientType(), command.getRecipientIds().size());
        
        return Mono.defer(() -> {
            // 获取当前租户 ID
            Long tenantId = TenantContext.currentTenantId();
            
            // 构建消息实体
            OfficeMessage message = OfficeMessage.builder()
                    .tenantId(tenantId)
                    .platform(command.getPlatform())
                    .appId(command.getAppId())
                    .messageType(command.getMessageType())
                    .recipientType(command.getRecipientType())
                    .recipientIds(command.getRecipientIds())
                    .title(command.getTitle())
                    .content(command.getContent())
                    .extraData(command.getExtraData())
                    .status(MessageStatus.PENDING)
                    .retryCount(0)
                    .maxRetryCount(command.getMaxRetryCount() != null ? command.getMaxRetryCount() : 3)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            // 调用领域服务发送消息
            return messageService.sendMessage(message);
        })
        .as(transactionalOperator::transactional)
        .doOnSuccess(message -> {
            if (message.isSuccess()) {
                log.info("消息发送成功，消息ID：{}，平台消息ID：{}", 
                        message.getId(), message.getPlatformMessageId());
            } else {
                log.warn("消息发送失败，消息ID：{}，错误信息：{}", 
                        message.getId(), message.getResultMessage());
            }
        })
        .doOnError(error -> {
            log.error("消息发送异常，平台：{}，错误信息：{}", 
                    command.getPlatform(), error.getMessage(), error);
        });
    }
    
    /**
     * 批量发送消息
     *
     * <p>向多个接收者批量发送相同的消息内容。
     *
     * @param command 发送消息命令
     * @return 发送后的消息实体
     */
    public Mono<OfficeMessage> sendBatchMessage(SendMessageCommand command) {
        log.info("开始批量发送消息，平台：{}，接收者数量：{}", 
                command.getPlatform(), command.getRecipientIds().size());
        
        return Mono.defer(() -> {
            Long tenantId = TenantContext.currentTenantId();
            
            OfficeMessage message = OfficeMessage.builder()
                    .tenantId(tenantId)
                    .platform(command.getPlatform())
                    .appId(command.getAppId())
                    .messageType(command.getMessageType())
                    .recipientType(command.getRecipientType())
                    .recipientIds(command.getRecipientIds())
                    .title(command.getTitle())
                    .content(command.getContent())
                    .extraData(command.getExtraData())
                    .status(MessageStatus.PENDING)
                    .retryCount(0)
                    .maxRetryCount(command.getMaxRetryCount() != null ? command.getMaxRetryCount() : 3)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            return messageService.sendBatchMessage(message);
        })
        .as(transactionalOperator::transactional)
        .doOnSuccess(message -> {
            log.info("批量消息发送完成，消息ID：{}，状态：{}", 
                    message.getId(), message.getStatus());
        })
        .doOnError(error -> {
            log.error("批量消息发送异常，错误信息：{}", error.getMessage(), error);
        });
    }
    
    /**
     * 重试发送失败的消息
     *
     * <p>对发送失败的消息进行重试，会检查重试次数限制。
     * 如果重试次数已达上限，则不再重试。
     *
     * @param messageId 消息 ID
     * @return 重试后的消息实体
     */
    public Mono<OfficeMessage> retryMessage(Long messageId) {
        log.info("开始重试消息，消息ID：{}", messageId);
        
        return messageService.retryMessage(messageId)
                .as(transactionalOperator::transactional)
                .doOnSuccess(message -> {
                    if (message.isSuccess()) {
                        log.info("消息重试成功，消息ID：{}", messageId);
                    } else {
                        log.warn("消息重试失败，消息ID：{}，错误信息：{}", 
                                messageId, message.getResultMessage());
                    }
                })
                .doOnError(error -> {
                    log.error("消息重试异常，消息ID：{}，错误信息：{}", 
                            messageId, error.getMessage(), error);
                });
    }
    
    /**
     * 查询消息发送状态
     *
     * <p>根据消息 ID 查询消息的发送状态和详细信息。
     *
     * @param messageId 消息 ID
     * @return 消息实体
     */
    public Mono<OfficeMessage> getMessageById(Long messageId) {
        log.debug("查询消息，消息ID：{}", messageId);
        
        return messageService.getMessageById(messageId)
                .doOnSuccess(message -> {
                    log.debug("查询消息成功，消息ID：，状态：{}", messageId, message.getStatus());
                })
                .doOnError(error -> {
                    log.error("查询消息异常，消息ID：{}，错误信息：{}", 
                            messageId, error.getMessage(), error);
                });
    }
    
    /**
     * 查询消息列表
     *
     * <p>根据平台类型和状态查询消息列表。
     *
     * @param platform 平台类型（可选）
     * @param status 消息状态（可选）
     * @param page 页码
     * @param size 每页大小
     * @return 消息列表
     */
    public Flux<OfficeMessage> listMessages(String platform, MessageStatus status, int page, int size) {
        log.debug("查询消息列表，平台：{}，状态：{}，页码：{}，大小：{}", 
                platform, status, page, size);
        
        // 这里需要在 Repository 中实现查询逻辑
        // 暂时返回空列表，待 Repository 实现后再完善
        return Flux.empty();
    }
    
    /**
     * 撤回消息
     *
     * <p>撤回已发送的消息（如果平台支持）。
     *
     * @param messageId 消息 ID
     * @return 是否成功撤回
     */
    public Mono<Boolean> recallMessage(Long messageId) {
        log.info("开始撤回消息，消息ID：{}", messageId);
        
        return messageService.recallMessage(messageId)
                .as(transactionalOperator::transactional)
                .doOnSuccess(recalled -> {
                    if (recalled) {
                        log.info("消息撤回成功，消息ID：{}", messageId);
                    } else {
                        log.warn("消息撤回失败，消息ID：{}", messageId);
                    }
                })
                .doOnError(error -> {
                    log.error("消息撤回异常，消息ID：{}，错误信息：{}", 
                            messageId, error.getMessage(), error);
                });
    }
    
    /**
     * 批量重试失败的消息
     *
     * <p>批量重试发送失败且未达重试上限的消息。
     *
     * @param maxRetryMessages 最大重试消息数量
     * @return 重试的消息数量
     */
    public Mono<Long> retryFailedMessages(int maxRetryMessages) {
        log.info("开始批量重试失败消息，最大数量：{}", maxRetryMessages);
        
        // 这里需要在 Repository 中实现查询失败消息的逻辑
        // 暂时返回 0，待 Repository 实现后再完善
        return Mono.just(0L)
                .doOnSuccess(count -> {
                    log.info("批量重试完成，重试消息数量：{}", count);
                })
                .doOnError(error -> {
                    log.error("批量重试异常，错误信息：{}", error.getMessage(), error);
                });
    }
}
