package com.apex.scaffold.office.domain.message;

/**
 * 消息内容接口
 *
 * <p>定义消息内容的通用规范，所有具体的消息内容类型（文本、Markdown、卡片）
 * 都需要实现此接口。
 *
 * @author Apex Team
 * @since 1.3.0
 */
public interface MessageContent {
    /**
     * 获取消息类型
     *
     * @return 消息类型
     */
    MessageType getType();

    /**
     * 获取消息文本内容
     *
     * @return 消息文本
     */
    String getText();

    /**
     * 验证消息内容是否有效
     *
     * @return 是否有效
     */
    boolean isValid();

    /**
     * 转换为平台特定的消息格式
     *
     * @return 平台消息内容
     */
    Object toPlatformContent();
}
