package com.apex.scaffold.knowledge.domain.gateway;

import com.apex.scaffold.knowledge.domain.model.ChatMessage;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * 模型流式对话端口（OpenAI 兼容协议的领域抽象）。
 *
 * <p>实现方负责 SSE 解析与错误转换；Flux 正常完成表示回答结束，
 * 错误信号向上传播给问答编排层转为 SSE error 事件。
 *
 * @author Apex Team
 * @since 1.3.2
 */
public interface ChatPort {

    /**
     * 流式补全对话。
     *
     * @param systemPrompt 系统提示词（含检索上下文与引用标记约定）
     * @param history 最近若干轮对话历史（不含本轮提问）
     * @param question 本轮用户提问
     * @return 增量文本流
     */
    Flux<String> streamChat(String systemPrompt, List<ChatMessage> history, String question);
}
