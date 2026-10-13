package com.apex.scaffold.office.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 办公消息持久化对象
 *
 * <p>对应数据库表 office_message，用于存储办公平台消息发送记录。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("office_message")
public class OfficeMessagePO {

    /**
     * 消息ID（主键）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 办公平台类型（FEISHU、DINGTALK、WECOM）
     */
    private String platform;

    /**
     * 应用ID
     */
    private Long appId;

    /**
     * 消息类型（TEXT、MARKDOWN、CARD、IMAGE、FILE）
     */
    private String messageType;

    /**
     * 接收者类型（USER、DEPARTMENT、GROUP、USER_LIST）
     */
    @TableField("target_type")
    private String recipientType;

    /**
     * 接收者ID列表（JSON数组）
     */
    @TableField("target_user_ids")
    private String recipientIds;

    /**
     * 消息标题
     */
    private String title;

    /**
     * 消息内容
     */
    @TableField(typeHandler = org.apache.ibatis.type.ClobTypeHandler.class)
    private String content;

    /**
     * 消息附加数据（JSON格式）
     */
    @TableField(typeHandler = org.apache.ibatis.type.ClobTypeHandler.class)
    private String extraData;

    /**
     * 发送状态（PENDING、SENDING、SUCCESS、FAILED、PARTIAL_SUCCESS）
     */
    @TableField("send_status")
    private String status;

    /**
     * 平台返回的消息ID
     */
    private String platformMessageId;

    /**
     * 发送结果消息
     */
    @TableField("failure_reason")
    private String resultMessage;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetryCount;

    /**
     * 发送时间
     */
    private LocalDateTime sentAt;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 创建者ID
     */
    private Long createdBy;

    /**
     * 更新者ID
     */
    private Long updatedBy;

    /**
     * 逻辑删除标记（0-未删除，1-已删除）
     */
    @TableLogic
    private Integer deleted;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
}
