package com.apex.scaffold.domain.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 用户登录事件
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public class UserLoggedInEvent extends AbstractDomainEvent {

    /** 用户 ID。 */
    private final Long userId;

    /** 登录 IP。 */
    private final String loginIp;

    /**
     * 创建用户登录成功事件。
     *
     * @param userId 用户 ID
     * @param loginIp 登录 IP
     */
    @JsonCreator
    public UserLoggedInEvent(@JsonProperty("userId") Long userId, @JsonProperty("loginIp") String loginIp) {
        super("USER_LOGGED_IN", null);
        this.userId = userId;
        this.loginIp = loginIp;
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
