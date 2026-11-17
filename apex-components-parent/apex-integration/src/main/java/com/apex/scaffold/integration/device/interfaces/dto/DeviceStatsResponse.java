package com.apex.scaffold.integration.device.interfaces.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备统计响应（方案 M5 / 批次 E3，D5 聚合门面消费）。
 *
 * <p>契约固定：响应 {@code data} 为含 {@code deviceCount} 字段的 JSON 对象（租户内设备总数），勿改字段名。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceStatsResponse {

    /** 租户内设备总数。 */
    private long deviceCount;
}
