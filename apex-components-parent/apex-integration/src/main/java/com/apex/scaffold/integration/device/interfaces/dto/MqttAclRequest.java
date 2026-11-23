package com.apex.scaffold.integration.device.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * MQTT 授权钩子请求（方案 M5 / 批次 E3，EMQX HTTP 授权口径）。
 *
 * <p>EMQX 在设备执行 pub/sub 时调用本端点，请求体遵循 EMQX HTTP Authorize 规范：
 * {@code username}、{@code topic}、{@code action}（publish / subscribe）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@Schema(name = "MqttAclRequest", description = "MQTT 授权钩子请求（EMQX HTTP Authorize 规范）；按租户 topic 命名空间返回 allow/deny")
public class MqttAclRequest {

    /** EMQX username（{@code {tenantCode}/{deviceKey}} 复合形态）。 */
    @Schema(description = "EMQX username，{tenantCode}/{deviceKey} 复合形态", example = "default/dev-0001")
    private String username;

    /** 被访问的 topic。 */
    @Schema(description = "被访问的 topic", example = "apex/default/dev-0001/up")
    private String topic;

    /** 动作：publish / subscribe。 */
    @Schema(
            description = "MQTT 动作",
            example = "publish",
            allowableValues = {"publish", "subscribe"})
    private String action;
}
