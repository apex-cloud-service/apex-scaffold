package com.apex.scaffold.domain.office.message;

import lombok.Builder;
import lombok.Value;

/**
 * 卡片消息内容
 *
 * <p>表示交互式卡片消息内容，支持按钮、表单等交互元素。
 * 各平台的卡片格式差异较大，需要针对平台适配。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Value
@Builder
public class CardMessageContent implements MessageContent {
    /**
     * 卡片标题
     */
    String title;
    
    /**
     * 卡片内容（JSON 格式）
     */
    String cardJson;
    
    /**
     * 卡片类型（各平台定义）
     */
    String cardType;
    
    @Override
    public MessageType getType() {
        return MessageType.CARD;
    }
    
    @Override
    public boolean isValid() {
        return cardJson != null && !cardJson.trim().isEmpty();
    }
    
    @Override
    public Object toPlatformContent() {
        return cardJson;
    }
}
