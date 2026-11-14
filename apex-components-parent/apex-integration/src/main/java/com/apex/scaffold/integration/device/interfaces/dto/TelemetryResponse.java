package com.apex.scaffold.integration.device.interfaces.dto;

import com.apex.scaffold.integration.device.domain.DeviceTelemetry;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 遥测响应（方案 M5 / 批次 E3）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
public class TelemetryResponse {

    private Long id;

    private String deviceKey;

    /** 遥测指标（JSON 原文）。 */
    private String metric;

    private LocalDateTime recordedAt;

    /**
     * 从遥测实体映射。
     *
     * @param telemetry 遥测实体
     * @return 响应
     */
    public static TelemetryResponse from(DeviceTelemetry telemetry) {
        TelemetryResponse response = new TelemetryResponse();
        response.setId(telemetry.getId());
        response.setDeviceKey(telemetry.getDeviceKey());
        response.setMetric(telemetry.getMetric());
        response.setRecordedAt(telemetry.getRecordedAt());
        return response;
    }
}
