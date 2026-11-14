package com.apex.scaffold.integration.device.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 设备遥测持久化对象（表 {@code iot_telemetry}）。
 *
 * <p>高频上行批量插入；保留窗口清理交由宿主运维任务，不在本域内删除历史。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@TableName("iot_telemetry")
public class DeviceTelemetryPO {

    /** 主键（雪花算法）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户编码。 */
    private String tenantId;

    /** 设备标识。 */
    private String deviceKey;

    /** 遥测指标（JSON 对象）。 */
    private String metric;

    /** 上报时间。 */
    private LocalDateTime recordedAt;

    /** 落库时间（DB 默认填充）。 */
    private LocalDateTime createTime;
}
