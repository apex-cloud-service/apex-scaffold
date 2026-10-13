package com.apex.scaffold.domain.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import lombok.Getter;

/**
 * 用户注册成功领域事件
 *
 * <p>当用户注册成功时发布的领域事件，包含用户的基本信息
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public class UserRegisteredEvent extends AbstractDomainEvent {

    /** 用户 ID */
    private final Long userId;

    /** 用户名 */
    private final String nickName;

    /** 手机号 */
    private final String phone;

    /**
     * 创建用户注册成功事件
     *
     * @param userId 用户 ID
     * @param nickName 用户名
     * @param phone 手机号
     */
    public UserRegisteredEvent(Long userId, String nickName, String phone) {
        super("USER_REGISTERED", null);
        this.userId = userId;
        this.nickName = nickName;
        this.phone = phone;
    }

    /**
     * 获取事件源ID。
     *
     * @return 用户ID的字符串表示
     */
    @Override
    public String getSourceId() {
        return String.valueOf(userId);
    }
}
