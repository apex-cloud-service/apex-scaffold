package com.apex.scaffold.domain.office.message;

/**
 * 消息发送状态枚举
 *
 * <p>定义消息的发送状态，用于跟踪消息的生命周期。
 *
 * @author Apex Team
 * @since 1.3.0
 */
public enum MessageStatus {
    
    /**
     * 待发送
     * 消息已创建，等待发送
     */
    PENDING,
    
    /**
     * 发送中
     * 消息正在发送过程中
     */
    SENDING,
    
    /**
     * 发送成功
     * 消息已成功发送到办公平台
     */
    SUCCESS,
    
    /**
     * 发送失败
     * 消息发送失败
     */
    FAILED,
    
    /**
     * 部分成功
     * 消息发送给多个接收者时，部分成功部分失败
     */
    PARTIAL_SUCCESS
}
