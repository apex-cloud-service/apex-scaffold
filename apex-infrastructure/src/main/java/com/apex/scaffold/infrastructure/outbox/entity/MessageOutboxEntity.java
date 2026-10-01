package com.apex.scaffold.infrastructure.outbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息发件箱实体类(Outbox模式)
 *
 * <p>本地消息表实体,用于实现Outbox模式,保证业务事务与消息发送的原子性。
 * 当业务操作和消息写入在同一事务内完成时,即使消息发送失败,
 * 后台调度器也会自动重试,确保消息最终投递成功。
 *
 * <h2>核心字段说明</h2>
 * <ul>
 *   <li>messageId: 全局唯一的消息标识,使用雪花算法生成</li>
 *   <li>topic: RocketMQ主题名称</li>
 *   <li>tag: RocketMQ消息标签,用于消费端过滤</li>
 *   <li>payload: 消息体JSON字符串</li>
 *   <li>status: 消息状态(PENDING/PROCESSING/DELIVERED/DEAD)</li>
 *   <li>attempts: 已尝试投递次数</li>
 *   <li>lockedBy: 分布式锁标识,防止多实例并发投递</li>
 * </ul>
 *
 * <h2>状态流转</h2>
 * <pre>
 * PENDING(待投递) → PROCESSING(投递中) → DELIVERED(已投递)
 *                                      ↓
 *                                   DEAD(死信)
 * </pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.infrastructure.outbox.service.MessageOutboxService
 * @see com.apex.scaffold.infrastructure.outbox.dispatcher.MessageOutboxDispatcher
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_message_outbox")
public class MessageOutboxEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String messageId;

    private String topic;

    private String tag;

    private String payload;

    private String messageType;

    private String status;

    private Integer attempts;

    private Integer maxAttempts;

    private LocalDateTime nextAttemptAt;

    private Integer retryDelaySeconds;

    private String lockedBy;

    private LocalDateTime lockedAt;

    private Integer lockTimeoutSeconds;

    private String lastError;

    private String tenantId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deliveredAt;

    public static class MessageType {
        public static final String DOMAIN_EVENT = "DOMAIN_EVENT";
        public static final String BUSINESS_MESSAGE = "BUSINESS_MESSAGE";
    }

    public static class Status {
        public static final String PENDING = "PENDING";
        public static final String PROCESSING = "PROCESSING";
        public static final String DELIVERED = "DELIVERED";
        public static final String DEAD = "DEAD";
    }
}
