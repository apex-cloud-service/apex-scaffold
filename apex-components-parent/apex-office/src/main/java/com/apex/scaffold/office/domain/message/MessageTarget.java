package com.apex.scaffold.office.domain.message;

import java.util.List;
import lombok.Builder;
import lombok.Value;

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
    private static final String TARGET_TYPE_USER = "USER";
    private static final String TARGET_TYPE_GROUP = "GROUP";

    String targetType;

    List<String> userIds;

    List<String> groupIds;

    public boolean isValid() {
        if (targetType == null) {
            return false;
        }

        if (TARGET_TYPE_USER.equals(targetType)) {
            return userIds != null && !userIds.isEmpty();
        } else if (TARGET_TYPE_GROUP.equals(targetType)) {
            return groupIds != null && !groupIds.isEmpty();
        }

        return false;
    }
}
