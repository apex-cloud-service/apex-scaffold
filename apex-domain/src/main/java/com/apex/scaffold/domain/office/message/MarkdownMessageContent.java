package com.apex.scaffold.domain.office.message;

import lombok.Builder;
import lombok.Value;

/**
 * Markdown 消息内容
 *
 * <p>表示 Markdown 格式的消息内容，支持富文本展示。
 * 各平台对 Markdown 语法的支持可能略有差异。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Value
@Builder
public class MarkdownMessageContent implements MessageContent {
    /**
     * Markdown 内容
     */
    String markdown;

    /**
     * 纯文本标题（可选，用于通知场景）
     */
    String title;

    @Override
    public MessageType getType() {
        return MessageType.MARKDOWN;
    }

    @Override
    public String getText() {
        return markdown;
    }

    @Override
    public boolean isValid() {
        return markdown != null && !markdown.trim().isEmpty();
    }

    @Override
    public Object toPlatformContent() {
        return markdown;
    }
}
