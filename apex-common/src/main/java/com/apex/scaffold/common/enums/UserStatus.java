package com.apex.scaffold.common.enums;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 用户状态枚举
 *
 * <p>定义用户的状态类型，包括正常和禁用两种状态
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
@RequiredArgsConstructor
public enum UserStatus {

    /**
     * 正常
     *
     * <p>用户状态正常，可以正常登录和使用系统
     */
    NORMAL(1, "正常"),

    /**
     * 禁用
     *
     * <p>用户状态禁用，无法登录和使用系统
     */
    DISABLED(0, "禁用");

    /** 状态编码 */
    private final Integer code;

    /** 状态描述 */
    private final String desc;

    /**
     * 根据code获取枚举。
     *
     * <p>通过状态编码查找对应的UserStatus枚举值。
     * 如果code为null或找不到匹配的枚举值，返回NORMAL作为默认值。</p>
     *
     * @param code 状态码
     * @return 用户状态枚举，若code为null或不匹配则返回 {@link #NORMAL}
     */
    public static UserStatus getByCode(Integer code) {
        if (code == null) {
            return NORMAL;
        }
        return Arrays.stream(values())
                .filter(s -> s.getCode().equals(code))
                .findFirst()
                .orElse(NORMAL);
    }
}
