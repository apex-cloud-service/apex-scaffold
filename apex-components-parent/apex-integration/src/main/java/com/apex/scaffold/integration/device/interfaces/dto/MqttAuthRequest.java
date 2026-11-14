package com.apex.scaffold.integration.device.interfaces.dto;

import lombok.Data;

/**
 * MQTT 认证钩子请求（方案 M5 / 批次 E3，EMQX HTTP 认证口径）。
 *
 * <p>生产由外部 EMQX 调用：{@code deviceKey/username + secret/password} 二组字段
 * 等价（username 亦可携带 {@code {tenantCode}/{deviceKey}} 复合形态）；设备认证
 * 通过后按租户 topic 命名空间做 ACL（{@code apex/{tenantCode}/{deviceKey}/up|down}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
public class MqttAuthRequest {

    /** 租户编码（可空；缺失时从 username 复合形态解析）。 */
    private String tenant;

    /** 设备标识（与 username 二选一）。 */
    private String deviceKey;

    /** EMQX username（可携带 {@code {tenantCode}/{deviceKey}} 复合形态）。 */
    private String username;

    /** 明文 secret（与 password 等价）。 */
    private String secret;

    /** EMQX password（与 secret 等价）。 */
    private String password;
}
