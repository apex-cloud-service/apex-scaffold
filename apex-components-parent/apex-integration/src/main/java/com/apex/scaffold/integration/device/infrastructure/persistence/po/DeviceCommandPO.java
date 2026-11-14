package com.apex.scaffold.integration.device.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 设备下行命令持久化对象（表 {@code iot_command}）。
 *
 * <p>状态机 {@code PENDING/SENT/ACK/TIMEOUT} 以条件更新推进；{@code commandId}
 * 为租户内唯一幂等键。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@TableName("iot_command")
public class DeviceCommandPO {

    /** 主键（雪花算法）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户编码。 */
    private String tenantId;

    /** 业务命令 ID（租户内唯一，幂等键）。 */
    private String commandId;

    /** 设备标识。 */
    private String deviceKey;

    /** 命令载荷（JSON）。 */
    private String payload;

    /** 状态（PENDING/SENT/ACK/TIMEOUT）。 */
    private String status;

    /** 下发时间。 */
    private LocalDateTime sentAt;

    /** 确认时间。 */
    private LocalDateTime ackedAt;

    /** 超时截止时间。 */
    private LocalDateTime timeoutAt;

    /** 创建时间（DB 默认填充）。 */
    private LocalDateTime createTime;

    /** 更新时间（DB ON UPDATE 填充）。 */
    private LocalDateTime updateTime;
}
