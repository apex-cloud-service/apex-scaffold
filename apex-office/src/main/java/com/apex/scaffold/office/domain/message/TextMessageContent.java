package com.apex.scaffold.office.domain.message;

import lombok.Builder;
import lombok.Value;

/**
 * 文本消息内容
 *
 * <p>表示纯文本格式的消息内容，是最基础的消息类型。
 * 所有办公平台都支持文本消息。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Value
@Builder
public class TextMessageContent implements MessageContent {
    /**
     * 文本内容
     */
    String text;

    @Override
    public MessageType getType() {
        return MessageType.TEXT;
    }

    @Override
    public boolean isValid() {
        return text != null && !text.trim().isEmpty();
    }

    @Override
    public Object toPlatformContent() {
        return text;
    }
}
