package com.apex.scaffold.domain.office.message;

import com.apex.scaffold.domain.office.OfficePlatform;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 办公平台消息聚合根
 *
 * <p>表示发送到办公平台的消息实体，包含消息的完整信息和状态。
 * 支持文本、Markdown、卡片等多种消息类型。
 *
 * <p>领域规则：
 * <ul>
 *   <li>消息必须关联到具体的办公平台</li>
 *   <li>消息必须有明确的接收目标（用户或群组）</li>
 *   <li>消息内容必须符合对应类型的格式要求</li>
 *   <li>消息发送后状态不可逆转（只能新增重试记录）</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfficeMessage {
    /**
     * 消息ID
     */
    private Long id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 办公平台类型
     */
    private OfficePlatform platform;

    /**
     * 消息类型
     */
    private MessageType messageType;

    /**
     * 消息目标
     */
    private MessageTarget target;

    /**
     * 消息内容
     */
    private MessageContent content;

    /**
     * 发送状态（PENDING/SUCCESS/FAILED）
     */
    private String sendStatus;

    /**
     * 平台消息ID（发送成功后由平台返回）
     */
    private String platformMessageId;

    /**
     * 失败原因
     */
    private String failureReason;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 发送时间
     */
    private LocalDateTime sentAt;

    /**
     * 版本号（乐观锁）
     */
    private Integer version;

    /**
     * 验证消息是否可以发送
     *
     * @return 是否可以发送
     */
    public boolean canSend() {
        return platform != null
                && messageType != null
                && target != null
                && target.isValid()
                && content != null
                && content.isValid()
                && !"SUCCESS".equals(sendStatus);
    }

    /**
     * 标记消息发送成功
     *
     * @param platformMessageId 平台消息ID
     */
    public void markAsSent(String platformMessageId) {
        this.sendStatus = "SUCCESS";
        this.platformMessageId = platformMessageId;
        this.sentAt = LocalDateTime.now();
    }

    /**
     * 标记消息发送失败
     *
     * @param reason 失败原因
     */
    public void markAsFailed(String reason) {
        this.sendStatus = "FAILED";
        this.failureReason = reason;
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }

    /**
     * 验证消息是否可以撤回
     *
     * <p>只有发送成功且已获得平台消息ID的消息才能撤回。
     *
     * @return 是否可以撤回
     */
    public boolean canRecall() {
        return "SUCCESS".equals(sendStatus) && platformMessageId != null && !platformMessageId.isBlank();
    }

    /**
     * 标记消息已撤回
     */
    public void markAsRecalled() {
        this.sendStatus = "RECALLED";
    }
}
