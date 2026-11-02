package com.apex.scaffold.integration.connector.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 出站调用审计持久化对象（表 {@code int_outbound_call_log}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@TableName("int_outbound_call_log")
public class OutboundCallLogPO {

    /** 主键（雪花算法）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户编码。 */
    private String tenantId;

    /** 连接器类型。 */
    private String connectorType;

    /** 操作标识。 */
    private String operation;

    /** 幂等键。 */
    private String idempotencyKey;

    /** 状态（SUCCESS/FAILED）。 */
    private String status;

    /** 耗时（毫秒）。 */
    private Long durationMs;

    /** 失败原因（脱敏）。 */
    private String errorMessage;

    /** 创建时间（DB 默认填充）。 */
    private LocalDateTime createTime;
}
