package com.apex.scaffold.domain.office.port;

import com.apex.scaffold.domain.office.message.MessageContent;
import com.apex.scaffold.domain.office.message.MessageTarget;
import reactor.core.publisher.Mono;

/**
 * 消息端口接口
 *
 * <p>定义消息领域的外部依赖接口，遵循端口-适配器架构模式。
 * 该接口由 Domain 层定义，由 Infrastructure 层实现，Application 层使用。
 *
 * <p>这是一个出站端口（Outbound Port），用于与外部办公平台的消息系统交互。
 * 通过依赖倒置原则，Application 层依赖此接口而非具体实现，保持架构清晰。
 *
 * @author Apex Scaffold
 * @since 1.2.0
 */
public interface MessagePort {

    /**
     * 发送消息
     *
     * @param target 消息目标（用户或群组）
     * @param content 消息内容
     * @return 平台消息ID
     */
    Mono<String> sendMessage(MessageTarget target, MessageContent content);

    /**
     * 查询消息状态
     *
     * @param messageId 平台消息ID
     * @return 消息状态信息
     */
    Mono<String> queryMessageStatus(String messageId);

    /**
     * 撤回消息
     *
     * @param messageId 平台消息ID
     * @return 撤回是否成功
     */
    Mono<Boolean> recallMessage(String messageId);

    /**
     * 获取支持的平台类型
     *
     * @return 平台类型标识（feishu/dingtalk/wecom）
     */
    String getPlatformType();
}
