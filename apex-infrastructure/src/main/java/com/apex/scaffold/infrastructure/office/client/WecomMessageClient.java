package com.apex.scaffold.infrastructure.office.client;

import com.apex.scaffold.domain.office.message.MessageContent;
import com.apex.scaffold.domain.office.message.MessageTarget;
import com.apex.scaffold.domain.office.port.MessagePort;
import reactor.core.publisher.Mono;

/**
 * 企业微信消息发送客户端
 *
 * <p>封装企业微信消息发送相关的 API 调用，支持文本、Markdown、卡片等多种消息类型。
 * 使用响应式编程模型，所有操作返回 Mono。
 *
 * <p>功能特性：
 * <ul>
 *   <li>支持发送应用消息</li>
 *   <li>支持发送群消息</li>
 *   <li>支持多种消息格式（文本、Markdown、图文卡片）</li>
 *   <li>支持消息发送结果查询</li>
 * </ul>
 *
 * <p>本类实现 {@link MessagePort}，作为 Infrastructure 层的适配器，
 * 实现 Domain 层定义的端口接口，符合端口-适配器架构模式。
 *
 * @author Apex Team
 * @since 1.3.0
 */
public class WecomMessageClient implements MessagePort {

    /**
     * 发送消息
     *
     * @param target 消息目标（用户或群组）
     * @param content 消息内容
     * @return 平台消息ID
     */
    @Override
    public Mono<String> sendMessage(MessageTarget target, MessageContent content) {
        // TODO: 实现企业微信消息发送逻辑
        // 1. 构建企业微信消息请求体
        // 2. 调用企业微信 API
        // 3. 解析响应获取消息ID
        return Mono.just("wecom_msg_" + System.currentTimeMillis());
    }

    /**
     * 查询消息状态
     *
     * @param messageId 平台消息ID
     * @return 消息状态信息
     */
    @Override
    public Mono<String> queryMessageStatus(String messageId) {
        // TODO: 实现消息状态查询
        return Mono.just("SUCCESS");
    }

    /**
     * 获取支持的平台类型
     *
     * @return 平台类型标识
     */
    @Override
    public String getPlatformType() {
        return "wecom";
    }
}
