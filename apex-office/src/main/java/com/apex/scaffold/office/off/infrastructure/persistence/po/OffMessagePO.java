package com.apex.scaffold.office.off.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 办公消息持久化对象
 *
 * <p>对应数据库表 {@code off_message}，存储站内消息的主体信息。
 * 继承 {@link BasePO} 获得通用字段（ID、创建时间、更新时间、逻辑删除等）。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>{@link #messageCode} - 消息编码（唯一标识）</li>
 *   <li>{@link #templateId} - 消息模板 ID（关联 {@code off_message_template}，可选）</li>
 *   <li>{@link #subject} - 消息主题</li>
 *   <li>{@link #content} - 消息内容（支持富文本）</li>
 *   <li>{@link #messageType} - 消息类型（SYSTEM_NOTIFY=系统通知、USER_MESSAGE=用户消息、APPROVAL_NOTIFY=审批通知）</li>
 *   <li>{@link #priority} - 优先级（LOW=低、NORMAL=普通、HIGH=高、URGENT=紧急）</li>
 *   <li>{@link #senderId} - 发送人 ID（系统消息为 null）</li>
 *   <li>{@link #senderName} - 发送人姓名（冗余字段，便于查询）</li>
 *   <li>{@link #sendTime} - 发送时间</li>
 *   <li>{@link #expireTime} - 过期时间（过期后消息自动失效）</li>
 *   <li>{@link #status} - 消息状态（DRAFT=草稿、SENDING=发送中、SENT=已发送、FAILED=发送失败）</li>
 *   <li>{@link #tenantId} - 租户标识（多租户隔离）</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>SYSTEM_NOTIFY：系统通知（如密码重置、账号变更）</li>
 *   <li>USER_MESSAGE：用户消息（如好友申请、消息提醒）</li>
 *   <li>APPROVAL_NOTIFY：审批通知（如审批提交、审批结果）</li>
 *   <li>TASK_NOTIFY：任务通知（如任务分配、任务到期）</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>DRAFT：草稿状态，未发送</li>
 *   <li>SENDING：发送中，正在分发给接收人</li>
 *   <li>SENT：已发送，消息已成功分发</li>
 *   <li>FAILED：发送失败，需要重试或人工处理</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>一对多：一条消息可以有多个接收人（通过 {@code off_message_recipient} 关联）</li>
 *   <li>多对一：消息可以关联一个模板（{@code templateId}）</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>站内通知：系统向用户发送通知消息</li>
 *   <li>用户消息：用户之间的私信交流</li>
 *   <li>批量消息：基于模板批量发送格式统一的消息</li>
 *   <li>定时消息：设置发送时间的延迟消息</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>消息编码 {@code messageCode} 应保持全局唯一</li>
 *   <li>过期时间 {@code expireTime} 用于自动清理历史消息</li>
 *   <li>消息内容 {@code content} 支持 HTML 格式，需防止 XSS 攻击</li>
 *   <li>租户隔离通过 {@code tenantId} 字段实现</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see BasePO
 * @see OffMessageTemplatePO
 * @see OffMessageRecipientPO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("off_message")
public class OffMessagePO extends BasePO {

    @TableField("message_code")
    private String messageCode;

    @TableField("template_id")
    private Long templateId;

    @TableField("subject")
    private String subject;

    @TableField("content")
    private String content;

    @TableField("message_type")
    private String messageType;

    @TableField("priority")
    private String priority;

    @TableField("sender_id")
    private Long senderId;

    @TableField("sender_name")
    private String senderName;

    @TableField("send_time")
    private LocalDateTime sendTime;

    @TableField("expire_time")
    private LocalDateTime expireTime;

    @TableField("status")
    private String status;

    @TableField("tenant_id")
    private String tenantId;
}
