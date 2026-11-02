package com.apex.scaffold.integration.device.interfaces.dto;

import com.apex.scaffold.integration.device.application.DeviceRegistration;
import com.apex.scaffold.integration.device.domain.Device;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备注册响应（方案 M5 / 批次 E3）。
 *
 * <p>{@code secret} 为服务端生成的明文凭据，仅在注册响应出现一次
 * （服务端仅存 PBKDF2 哈希）；设备侧须自行持久化。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceRegisterResponse extends DeviceResponse {

    /** 明文 secret（仅此一次）。 */
    private String secret;

    /**
     * 从注册结果映射。
     *
     * @param registration 注册结果
     * @return 响应（含一次性 secret）
     */
    public static DeviceRegisterResponse from(DeviceRegistration registration) {
        DeviceRegisterResponse response = new DeviceRegisterResponse();
        Device device = registration.device();
        response.setId(device.getId());
        response.setTenantId(device.getTenantId());
        response.setDeviceKey(device.getDeviceKey());
        response.setCertFingerprint(device.getCertFingerprint());
        response.setStatus(device.getStatus());
        response.setFirmwareVersion(device.getFirmwareVersion());
        response.setSecret(registration.plainSecret());
        return response;
    }
}
