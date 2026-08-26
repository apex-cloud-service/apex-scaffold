package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 系统消息实体类
 *
 * <p>存储系统向用户发送的消息通知
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class SystemMessageEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 消息类型 */
    private String msgType;

    /** 消息内容 */
    private String content;

    /** 关联类型 */
    private String refType;

    /** 关联ID */
    private Long refId;

    /** 已读时间 */
    private LocalDateTime readAt;

    /** Sets userId. For persistence/construction use only. */
    public void setUserId(Long value) {
        this.userId = value;
    }

    /** Sets msgType. For persistence/construction use only. */
    public void setMsgType(String value) {
        this.msgType = value;
    }

    /** Sets content. For persistence/construction use only. */
    public void setContent(String value) {
        this.content = value;
    }

    /** Sets refType. For persistence/construction use only. */
    public void setRefType(String value) {
        this.refType = value;
    }

    /** Sets refId. For persistence/construction use only. */
    public void setRefId(Long value) {
        this.refId = value;
    }

    /** Sets readAt. For persistence/construction use only. */
    public void setReadAt(LocalDateTime value) {
        this.readAt = value;
    }
}
