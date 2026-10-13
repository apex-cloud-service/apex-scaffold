package com.apex.scaffold.office.domain.message;

/**
 * 消息接收者类型枚举
 *
 * <p>定义消息的接收者类型，包括单个用户、部门、群组等。
 *
 * @author Apex Team
 * @since 1.3.0
 */
public enum RecipientType {

    /**
     * 单个用户
     * 发送给特定的单个用户
     */
    USER,

    /**
     * 部门
     * 发送给整个部门的所有成员
     */
    DEPARTMENT,

    /**
     * 群组
     * 发送给群组/群聊
     */
    GROUP,

    /**
     * 自定义用户列表
     * 发送给指定的多个用户
     */
    USER_LIST
}
