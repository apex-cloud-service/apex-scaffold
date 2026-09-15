package com.apex.scaffold.infrastructure.office.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.office.message.MessageStatus;
import com.apex.scaffold.domain.office.message.OfficeMessage;
import com.apex.scaffold.domain.office.message.OfficeMessageRepository;
import com.apex.scaffold.domain.office.message.OfficeMessageService;
import com.apex.scaffold.infrastructure.office.spi.OfficePlatformMessageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 办公平台消息领域服务实现
 *
 * <p>实现消息发送的核心业务逻辑，协调消息客户端和仓储完成消息发送。
 *
 * <p>主要功能：
 * <ul>
 *   <li>根据平台类型选择对应的客户端发送消息</li>
 *   <li>记录消息发送历史</li>
 *   <li>处理消息发送失败和重试</li>
 *   <li>提供消息撤回功能</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeMessageServiceImpl implements OfficeMessageService {
    
    private final List<OfficePlatformMessageClient> messageClients;
    private final OfficeMessageRepository messageRepository;
    
    private Map<String, OfficePlatformMessageClient> clientMap;
    
    /**
     * 初始化客户端映射
     */
    private void initClientMap() {
        if (clientMap == null) {
            clientMap = messageClients.stream()
                    .collect(Collectors.toMap(
                            OfficePlatformMessageClient::getPlatform,
                            Function.identity()
                    ));
        }
    }
    
    @Override
    public Mono<OfficeMessage> sendMessage(OfficeMessage message) {
        log.info("发送消息，平台：{}，类型：{}", message.getPlatform(), message.getMessageType());
        
        // 初始化客户端映射
        initClientMap();
        
        // 获取对应平台的客户端
        OfficePlatformMessageClient client = clientMap.get(message.getPlatform());
        if (client == null) {
            return Mono.error(new BusinessException(ResultCode.PARAM_INVALID, 
                    "不支持的平台类型：" + message.getPlatform()));
        }
        
        // 标记消息为发送中
        message.markAsSending();
        
        // 先保存消息记录
        return messageRepository.save(message)
                .flatMap(savedMessage -> {
                    // 调用客户端发送消息
                    return client.sendMessage(savedMessage)
                            .flatMap(platformMessageId -> {
                                // 标记发送成功
                                savedMessage.markAsSuccess(platformMessageId);
                                savedMessage.setUpdatedAt(LocalDateTime.now());
                                return messageRepository.save(savedMessage);
                            })
                            .onErrorResume(error -> {
                                // 标记发送失败
                                savedMessage.markAsFailed(error.getMessage());
                                savedMessage.setUpdatedAt(LocalDateTime.now());
                                return messageRepository.save(savedMessage);
                            });
                });
    }
    
    @Override
    public Mono<OfficeMessage> sendBatchMessage(OfficeMessage message) {
        log.info("批量发送消息，平台：{}，接收者数量：{}", 
                message.getPlatform(), message.getRecipientIds().size());
        
        initClientMap();
        
        OfficePlatformMessageClient client = clientMap.get(message.getPlatform());
        if (client == null) {
            return Mono.error(new BusinessException(ResultCode.PARAM_INVALID, 
                    "不支持的平台类型：" + message.getPlatform()));
        }
        
        message.markAsSending();
        
        return messageRepository.save(message)
                .flatMap(savedMessage -> {
                    return client.sendBatchMessage(savedMessage)
                            .flatMap(result -> {
                                savedMessage.markAsSuccess(result);
                                savedMessage.setUpdatedAt(LocalDateTime.now());
                                return messageRepository.save(savedMessage);
                            })
                            .onErrorResume(error -> {
                                savedMessage.markAsFailed(error.getMessage());
                                savedMessage.setUpdatedAt(LocalDateTime.now());
                                return messageRepository.save(savedMessage);
                            });
                });
    }
    
    @Override
    public Mono<OfficeMessage> retryMessage(Long messageId) {
        log.info("重试消息，消息ID：{}", messageId);
        
        return messageRepository.findById(messageId)
                .switchIfEmpty(Mono.error(new BusinessException(
                        ResultCode.RESOURCE_NOT_FOUND, "消息不存在")))
                .flatMap(message -> {
                    // 检查是否可以重试
                    if (!message.canRetry()) {
                        return Mono.error(new BusinessException(
                                ResultCode.OPERATION_FAILED, "消息已达最大重试次数"));
                    }
                    
                    // 增加重试次数
                    message.incrementRetryCount();
                    
                    // 重新发送
                    return sendMessage(message);
                });
    }
    
    @Override
    public Mono<OfficeMessage> getMessageById(Long messageId) {
        log.debug("查询消息，消息ID：{}", messageId);
        
        return messageRepository.findById(messageId)
                .switchIfEmpty(Mono.error(new BusinessException(
                        ResultCode.RESOURCE_NOT_FOUND, "消息不存在")));
    }
    
    @Override
    public Mono<Boolean> recallMessage(Long messageId) {
        log.info("撤回消息，消息ID：{}", messageId);
        
        initClientMap();
        
        return messageRepository.findById(messageId)
                .switchIfEmpty(Mono.error(new BusinessException(
                        ResultCode.RESOURCE_NOT_FOUND, "消息不存在")))
                .flatMap(message -> {
                    // 检查消息状态
                    if (!message.isSuccess()) {
                        return Mono.error(new BusinessException(
                                ResultCode.OPERATION_FAILED, "只能撤回发送成功的消息"));
                    }
                    
                    // 获取客户端
                    OfficePlatformMessageClient client = clientMap.get(message.getPlatform());
                    if (client == null) {
                        return Mono.error(new BusinessException(
                                ResultCode.PARAM_INVALID, "不支持的平台类型"));
                    }
                    
                    // 调用客户端撤回消息
                    return client.recallMessage(message.getPlatformMessageId())
                            .flatMap(recalled -> {
                                if (recalled) {
                                    // 更新消息状态（可以增加一个 RECALLED 状态）
                                    message.setUpdatedAt(LocalDateTime.now());
                                    return messageRepository.save(message)
                                            .thenReturn(true);
                                }
                                return Mono.just(false);
                            });
                });
    }
}
