package com.apex.scaffold.office.infrastructure.spi;

import com.apex.scaffold.office.domain.message.OfficeMessage;
import reactor.core.publisher.Mono;

/**
 * 办公平台消息客户端 SPI 接口
 *
 * <p>定义办公平台消息发送的标准接口，各平台（飞书、钉钉、企业微信）需实现此接口。
 *
 * <p>主要功能：
 * <ul>
 *   <li>发送文本消息</li>
 *   <li>发送Markdown消息</li>
 *   <li>发送卡片消息</li>
 *   <li>撤回消息</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
public interface OfficePlatformMessageClient {

    /**
     * 获取支持的平台类型
     *
     * @return 平台类型（FEISHU、DINGTALK、WECOM）
     */
    String getPlatform();

    /**
     * 发送消息
     *
     * <p>根据消息类型和接收者类型，发送消息到对应的办公平台。
     *
     * @param message 消息实体
     * @return 平台返回的消息ID
     */
    Mono<String> sendMessage(OfficeMessage message);

    /**
     * 批量发送消息
     *
     * <p>向多个接收者批量发送相同的消息内容。
     *
     * @param message 消息实体
     * @return 发送结果（成功数量、失败数量等）
     */
    Mono<String> sendBatchMessage(OfficeMessage message);

    /**
     * 撤回消息
     *
     * <p>撤回已发送的消息（如果平台支持）。
     *
     * @param platformMessageId 平台消息ID
     * @return 是否成功撤回
     */
    Mono<Boolean> recallMessage(String platformMessageId);
}
