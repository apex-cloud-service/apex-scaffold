package com.apex.scaffold.domain.model.entity;

import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 消息实体
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class OffMessageEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String messageCode;
    private Long templateId;
    private String subject;
    private String content;
    private String messageType;
    private String priority;
    private Long senderId;
    private String senderName;
    private LocalDateTime sendTime;
    private LocalDateTime expireTime;
    private String status;
    private String tenantId;

    public OffMessageEntity() {
        super();
    }

    /**
     * 设置消息编码，赋值后刷新更新时间。
     *
     * @param messageCode 消息的业务编码
     */
    public void setMessageCode(String messageCode) {
        this.messageCode = messageCode;
        touch();
    }

    /**
     * 设置消息模板 ID，赋值后刷新更新时间。
     *
     * @param templateId 关联的消息模板 ID，可为空表示不使用模板
     */
    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
        touch();
    }

    /**
     * 设置消息主题，赋值后刷新更新时间。
     *
     * @param subject 消息主题（标题）
     */
    public void setSubject(String subject) {
        this.subject = subject;
        touch();
    }

    /**
     * 设置消息内容，赋值后刷新更新时间。
     *
     * @param content 消息正文内容
     */
    public void setContent(String content) {
        this.content = content;
        touch();
    }

    /**
     * 设置消息类型，赋值后刷新更新时间。
     *
     * @param messageType 消息类型标识
     */
    public void setMessageType(String messageType) {
        this.messageType = messageType;
        touch();
    }

    /**
     * 设置消息优先级，赋值后刷新更新时间。
     *
     * @param priority 消息优先级（如 NORMAL）
     */
    public void setPriority(String priority) {
        this.priority = priority;
        touch();
    }

    /**
     * 设置发送人 ID，赋值后刷新更新时间。
     *
     * @param senderId 发送人用户 ID
     */
    public void setSenderId(Long senderId) {
        this.senderId = senderId;
        touch();
    }

    /**
     * 设置发送人姓名，赋值后刷新更新时间。
     *
     * @param senderName 发送人显示名称
     */
    public void setSenderName(String senderName) {
        this.senderName = senderName;
        touch();
    }

    /**
     * 设置发送时间，赋值后刷新更新时间。
     *
     * @param sendTime 消息发送时间
     */
    public void setSendTime(LocalDateTime sendTime) {
        this.sendTime = sendTime;
        touch();
    }

    /**
     * 设置过期时间，赋值后刷新更新时间。
     *
     * @param expireTime 消息失效时间，null 表示长期有效
     */
    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
        touch();
    }

    /**
     * 设置消息状态，赋值后刷新更新时间。
     *
     * @param status 消息状态（如 SENT）
     */
    public void setStatus(String status) {
        this.status = status;
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
