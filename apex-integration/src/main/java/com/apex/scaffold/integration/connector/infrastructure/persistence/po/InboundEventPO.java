package com.apex.scaffold.integration.connector.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 入站事件持久化对象（表 {@code int_inbound_event}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@TableName("int_inbound_event")
public class InboundEventPO {

    /** 主键（雪花算法）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户编码。 */
    private String tenantId;

    /** 连接器类型。 */
    private String connectorType;

    /** 事件类型。 */
    private String eventType;

    /** 事件载荷（JSON）。 */
    private String payload;

    /** 签名校验（0-失败，1-通过）。 */
    private Integer signatureValid;

    /** 处理状态（0-待处理，1-已处理）。 */
    private Integer processed;

    /** 创建时间（DB 默认填充）。 */
    private LocalDateTime createTime;
}
