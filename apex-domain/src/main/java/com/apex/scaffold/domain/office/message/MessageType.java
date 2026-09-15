package com.apex.scaffold.domain.office.message;

/**
 * 消息类型枚举
 *
 * <p>定义办公平台支持的消息类型，用于标识消息的格式和展示方式。
 *
 * @author Apex Team
 * @since 1.3.0
 */
public enum MessageType {
    /**
     * 纯文本消息
     */
    TEXT,
    
    /**
     * Markdown 格式消息
     */
    MARKDOWN,
    
    /**
     * 交互式卡片消息
     */
    CARD
}
