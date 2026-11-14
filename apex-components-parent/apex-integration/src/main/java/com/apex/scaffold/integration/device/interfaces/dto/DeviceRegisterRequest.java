package com.apex.scaffold.integration.device.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

/**
 * 设备注册请求（方案 M5 / 批次 E3）。
 *
 * <p>secret 由服务端生成，不在请求中出现；明文 secret 仅在注册响应返回一次。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
public class DeviceRegisterRequest {

    /** 设备标识（租户内唯一，topic 命名空间段）。 */
    @NotBlank(message = "设备标识不能为空")
    @Size(max = 64, message = "设备标识最长 64 位")
    private String deviceKey;

    /** X.509 证书指纹（可空）。 */
    @Size(max = 128, message = "证书指纹最长 128 位")
    private String certFingerprint;

    /** 固件版本（可空）。 */
    @Size(max = 64, message = "固件版本最长 64 位")
    private String firmwareVersion;

    /** 标签（可空，键值对；序列化为 JSON 存储）。 */
    private Map<String, String> labels;
}
