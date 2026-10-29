package com.apex.scaffold.office.off.application.service;

import java.util.List;

/**
 * 站内消息本地事务已提交事件，仅作为在线提示，不提供持久投递保证。
 * @param tenantId 可信租户
 * @param messageId 已提交消息标识
 * @param recipientIds 接收用户
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public record MessageCommittedEvent(String tenantId, Long messageId, List<Long> recipientIds) {

    /** 事件契约版本，供下游消费者做兼容判断。 */
    public static final int SCHEMA_VERSION = 1;
}
