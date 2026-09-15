package com.apex.scaffold.domain.office.message;

import reactor.core.publisher.Mono;

/**
 * 办公平台消息服务接口
 *
 * <p>定义消息发送的核心领域服务接口，提供跨办公平台的消息发送能力。
 * 实现类负责与具体的办公平台（飞书、钉钉、企业微信）进行交互。
 *
 * <p>主要功能：
 * <ul>
 *   <li>发送各种类型的消息（文本、Markdown、卡片等）</li>
 *   <li>支持多种接收者类型（用户、部门、群组）</li>
 *   <li>记录消息发送历史和状态</li>
 *   <li>提供消息重试机制</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
public interface OfficeMessageService {
    
    /**
     * 发送消息到办公平台
     *
     * <p>根据消息的平台类型和配置，将消息发送到对应的办公平台。
     * 支持文本、Markdown、卡片等多种消息类型。
     *
     * @param message 消息实体，包含消息内容、接收者、平台等信息
     * @return 发送后的消息实体（包含平台返回的消息 ID 和状态）
     */
    Mono<OfficeMessage> sendMessage(OfficeMessage message);
    
    /**
     * 批量发送消息
     *
     * <p>向多个接收者批量发送相同的消息内容。
     *
     * @param message 消息实体
     * @return 发送后的消息实体
     */
    Mono<OfficeMessage> sendBatchMessage(OfficeMessage message);
    
    /**
     * 重试发送失败的消息
     *
     * <p>对发送失败的消息进行重试，会检查重试次数限制。
     *
     * @param messageId 消息 ID
     * @return 重试后的消息实体
     */
    Mono<OfficeMessage> retryMessage(Long messageId);
    
    /**
     * 查询消息发送状态
     *
     * <p>根据消息 ID 查询消息的发送状态和详细信息。
     *
     * @param messageId 消息 ID
     * @return 消息实体
     */
    Mono<OfficeMessage> getMessageById(Long messageId);
    
    /**
     * 撤回消息
     *
     * <p>撤回已发送的消息（如果平台支持）。
     *
     * @param messageId 消息 ID
     * @return 是否成功撤回
     */
    Mono<Boolean> recallMessage(Long messageId);
}
