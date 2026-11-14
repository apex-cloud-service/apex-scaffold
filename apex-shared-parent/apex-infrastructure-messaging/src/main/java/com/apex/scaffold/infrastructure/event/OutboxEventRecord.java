package com.apex.scaffold.infrastructure.event;

/**
 * 已被 Outbox 调度器领取的领域事件记录。
 *
 * @param id 数据库主键
 * @param eventId 领域事件唯一标识
 * @param eventClass 事件 Java 类型
 * @param payload 事件 JSON 负载
 * @param attempts 已失败次数
 * @author Apex Team
 * @version 1.0.0
 * @since 3.2.0
 */
public record OutboxEventRecord(long id, String eventId, String eventClass, String payload, int attempts) {}
