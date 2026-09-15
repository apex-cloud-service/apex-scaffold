package com.apex.scaffold.domain.office.message;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 消息目标值对象
 *
 * <p>定义消息的接收目标，支持用户和群组两种类型。
 * 可以同时指定多个接收者。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Value
@Builder
public class MessageTarget {
    /**
     * 目标类型（USER/GROUP）
     */
    String targetType;
    
    /**
     * 接收用户ID列表（平台用户ID）
     */
    List<String> userIds;
    
    /**
     * 接收群组ID列表（平台群组ID）
     */
    List<String> groupIds;
    
    /**
     * 验证消息目标是否有效
     *
     * @return 是否有效
     */
    public boolean isValid() {
        if (targetType == null) {
            return false;
        }
        
        if ("USER".equals(targetType)) {
            return userIds != null && !userIds.isEmpty();
        } else if ("GROUP".equals(targetType)) {
            return groupIds != null && !groupIds.isEmpty();
        }
        
        return false;
    }
}
