package com.apex.scaffold.integration.device.interfaces.dto;

import com.apex.scaffold.integration.device.domain.DeviceTelemetry;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "TelemetryResponse", description = "设备遥测响应；metric 为遥测指标 JSON 原文")
public class TelemetryResponse {

    @Schema(description = "遥测主键 ID", example = "1", type = "integer", format = "int64")
    private Long id;

    @Schema(description = "设备标识", example = "dev-0001")
    private String deviceKey;

    /** 遥测指标（JSON 原文）。 */
    @Schema(description = "遥测指标 JSON 原文", example = "{\"temperature\":82.5,\"humidity\":41}")
    private String metric;

    @Schema(description = "采集时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
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
