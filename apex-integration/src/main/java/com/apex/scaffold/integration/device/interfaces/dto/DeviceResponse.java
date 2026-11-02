package com.apex.scaffold.integration.device.interfaces.dto;

import com.apex.scaffold.integration.device.domain.Device;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class DeviceResponse {

    private Long id;

    private String tenantId;

    private String deviceKey;

    private String certFingerprint;

    /** 状态（online/offline）。 */
    private String status;

    private String firmwareVersion;

    private Map<String, String> labels;

    private LocalDateTime lastActiveAt;

    private LocalDateTime createTime;

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
