package com.apex.scaffold.office.off.domain.entity;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 消息接收人实体
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class OffMessageRecipientEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long messageId;
    private Long recipientId;
    private String recipientName;
    private String recipientType;
    private Boolean readStatus;
    private LocalDateTime readTime;
    private String tenantId;

    public OffMessageRecipientEntity() {
        super();
    }

    /**
     * 设置所属消息 ID，赋值后刷新更新时间。
     *
     * @param messageId 关联的办公消息 ID
     */
    public void setMessageId(Long messageId) {
        this.messageId = messageId;
        touch();
    }

    /**
     * 设置接收人 ID，赋值后刷新更新时间。
     *
     * @param recipientId 消息接收人用户 ID
     */
    public void setRecipientId(Long recipientId) {
        this.recipientId = recipientId;
        touch();
    }

    /**
     * 设置接收人姓名，赋值后刷新更新时间。
     *
     * @param recipientName 接收人显示名称
     */
    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
        touch();
    }

    /**
     * 设置接收人类型，赋值后刷新更新时间。
     *
     * @param recipientType 接收人类型标识（如用户、部门）
     */
    public void setRecipientType(String recipientType) {
        this.recipientType = recipientType;
        touch();
    }

    /**
     * 设置已读状态，赋值后刷新更新时间。
     *
     * @param readStatus 是否已读（true-已读，false-未读）
     */
    public void setReadStatus(Boolean readStatus) {
        this.readStatus = readStatus;
        touch();
    }

    /**
     * 设置已读时间，赋值后刷新更新时间。
     *
     * @param readTime 接收人阅读消息的时间，未读时为 null
     */
    public void setReadTime(LocalDateTime readTime) {
        this.readTime = readTime;
        touch();
    }

    /**
     * 设置租户标识，赋值后刷新更新时间。
     *
     * @param tenantId 租户标识，用于多租户数据隔离
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        touch();
    }
}
