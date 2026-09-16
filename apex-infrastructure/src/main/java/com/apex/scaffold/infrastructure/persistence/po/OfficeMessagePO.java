package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 办公平台消息持久化对象
 *
 * <p>对应数据库表 office_message，用于存储消息发送记录。
 * 支持租户隔离、逻辑删除和乐观锁。
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
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 办公平台类型
     */
    private String platform;

    /**
     * 应用ID
     */
    private Long appId;

    /**
     * 消息类型
     */
    private String messageType;

    /**
     * 目标类型
     */
    private String targetType;

    /**
     * 目标用户ID列表（JSON）
     */
    private String targetUserIds;

    /**
     * 目标群组ID列表（JSON）
     */
    private String targetGroupIds;

    /**
     * 消息内容（JSON）
     */
    private String content;

    /**
     * 发送状态
     */
    private String sendStatus;

    /**
     * 平台消息ID
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
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Integer deleted;

    /**
     * 版本号（乐观锁）
     */
    @Version
    private Integer version;
}
