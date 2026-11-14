package com.apex.scaffold.integration.connector.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 入站 webhook 幂等去重持久化对象（表 {@code int_webhook_dedup}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@TableName("int_webhook_dedup")
public class WebhookDedupPO {

    /** 主键（雪花算法）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户编码。 */
    private String tenantId;

    /** 去重键（签名+时间戳+事件ID 摘要）。 */
    private String dedupKey;

    /** 过期时间。 */
    private LocalDateTime expireAt;

    /** 创建时间（DB 默认填充）。 */
    private LocalDateTime createTime;
}
