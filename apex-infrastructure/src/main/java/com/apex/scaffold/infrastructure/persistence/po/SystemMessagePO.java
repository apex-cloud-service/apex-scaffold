package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 系统消息持久化对象。
 *
 * <p>对应数据库表 biz_system_message，记录系统通知类消息，
 * 包括消息类型、内容、关联数据及阅读状态。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("biz_system_message")
public class SystemMessagePO extends BasePO {

    /** 接收用户 ID。 */
    @TableField("user_id")
    private Long userId;

    /** 消息类型。 */
    @TableField("msg_type")
    private String msgType;

    /** 文案内容。 */
    @TableField("content")
    private String content;

    /** 关联类型。 */
    @TableField("ref_type")
    private String refType;

    /** 关联 ID。 */
    @TableField("ref_id")
    private Long refId;

    /** 阅读时间。 */
    @TableField("read_at")
    private LocalDateTime readAt;
}
