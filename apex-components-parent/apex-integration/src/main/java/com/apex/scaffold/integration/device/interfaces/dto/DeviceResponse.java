package com.apex.scaffold.integration.device.interfaces.dto;

import com.apex.scaffold.integration.device.domain.Device;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

/**
 * 设备响应（方案 M5 / 批次 E3）。
 *
 * <p>不含 {@code secretHash}（凭据哈希不对外透出）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@Schema(name = "DeviceResponse", description = "设备响应；不含凭据哈希")
public class DeviceResponse {

    @Schema(description = "设备主键 ID", example = "1", type = "integer", format = "int64")
    private Long id;

    @Schema(description = "所属租户 ID", example = "default")
    private String tenantId;

    @Schema(description = "设备标识", example = "dev-0001")
    private String deviceKey;

    @Schema(description = "X.509 证书指纹", example = "AB:CD:EF:01:23:45:67:89")
    private String certFingerprint;

    /** 状态（online/offline）。 */
    @Schema(
            description = "设备在线状态",
            example = "online",
            allowableValues = {"online", "offline"})
    private String status;

    @Schema(description = "固件版本", example = "1.4.2")
    private String firmwareVersion;

    @Schema(description = "设备标签键值对", example = "{\"site\":\"shenzhen\",\"line\":\"A\"}")
    private Map<String, String> labels;

    @Schema(description = "最近活跃时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
    private LocalDateTime lastActiveAt;

    @Schema(description = "创建时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
    private LocalDateTime updateTime;

    /**
     * 从设备实体映射（剔除凭据哈希）。
     *
     * @param device 设备实体
     * @return 响应
     */
    public static DeviceResponse from(Device device) {
        DeviceResponse response = new DeviceResponse();
        response.setId(device.getId());
        response.setTenantId(device.getTenantId());
        response.setDeviceKey(device.getDeviceKey());
        response.setCertFingerprint(device.getCertFingerprint());
        response.setStatus(device.getStatus());
        response.setFirmwareVersion(device.getFirmwareVersion());
        response.setLabels(parseLabels(device.getLabels()));
        response.setLastActiveAt(device.getLastActiveAt());
        response.setCreateTime(device.getCreateTime());
        response.setUpdateTime(device.getUpdateTime());
        return response;
    }

    private static Map<String, String> parseLabels(String labelsJson) {
        if (labelsJson == null || labelsJson.isBlank()) {
            return Map.of();
        }
        try {
            return new ObjectMapper().readValue(labelsJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
