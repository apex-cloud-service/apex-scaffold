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
 * <p>保存待投递内容、租户作用域、重试进度和锁状态。业务数据与消息记录的原子提交
 * 依赖调用方使用同一个本地事务；实体本身不提供事务或消息发送能力。
 * 后续投递由调度器执行，达到尝试上限后可进入死信状态，不保证最终发送成功。
 *
 * <h2>核心字段说明</h2>
 * <ul>
 *   <li>messageId: 调用方提供的消息标识，实体不负责生成</li>
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

    /** 数据库自增主键，与业务消息标识分离。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调用方提供的业务消息标识，供锁定和状态更新定位消息。 */
    private String messageId;

    /** 目标 RocketMQ 主题。 */
    private String topic;

    /** 目标消息标签，用于消费端过滤。 */
    private String tag;

    /** 已序列化的消息内容，写入前须按业务要求处理敏感数据。 */
    private String payload;

    /** 持久化消息分类，取值参见 {@link MessageType}。 */
    private String messageType;

    /** 当前投递状态，取值参见 {@link Status}。 */
    private String status;

    /** 服务记录的累计失败次数，失败处理时递增。 */
    private Integer attempts;

    /** 累计失败次数达到该值后转入死信状态。 */
    private Integer maxAttempts;

    /** 待投递查询允许再次选中该消息的最早本地日期时间。 */
    private LocalDateTime nextAttemptAt;

    /** 指数退避重试的基础延迟，单位为秒。 */
    private Integer retryDelaySeconds;

    /** 当前获得处理权的投递实例标识。 */
    private String lockedBy;

    /** 当前处理锁的取得时间，供过期锁回收判断。 */
    private LocalDateTime lockedAt;

    /** 处理锁超时长度，单位为秒。 */
    private Integer lockTimeoutSeconds;

    /** 最近一次投递失败的错误摘要，调用方须去除凭据等敏感内容。 */
    private String lastError;

    /** 由可信业务上下文提供的租户标识，实体本身不校验或传播租户。 */
    private String tenantId;

    /** 消息记录创建时间。 */
    private LocalDateTime createdAt;

    /** 消息记录最近更新时间。 */
    private LocalDateTime updatedAt;

    /** 标记投递完成的时间，不代表消费端业务已处理成功。 */
    private LocalDateTime deliveredAt;

    /** 区分领域事件与普通业务消息的持久化类型值，供投递方选择处理路径。 */
    public static class MessageType {
        /** 领域事件消息。 */
        public static final String DOMAIN_EVENT = "DOMAIN_EVENT";

        /** 普通业务消息。 */
        public static final String BUSINESS_MESSAGE = "BUSINESS_MESSAGE";
    }

    /** 发件箱持久化状态；失败后可回到待投递，达到尝试上限后进入死信状态。 */
    public static class Status {
        /** 等待首次投递或下次重试。 */
        public static final String PENDING = "PENDING";

        /** 已由投递实例取得处理权。 */
        public static final String PROCESSING = "PROCESSING";

        /** 投递流程已记录发送完成，不表示消费端确认。 */
        public static final String DELIVERED = "DELIVERED";

        /** 失败次数达到上限，不再进入常规待投递查询。 */
        public static final String DEAD = "DEAD";
    }
}
