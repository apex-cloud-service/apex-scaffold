package com.apex.scaffold.integration.device.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 规则创建请求（方案 M5 / 批次 E3）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@Schema(name = "RuleCreateRequest", description = "设备规则创建请求；conditionJson 仅支持声明式比较")
public class RuleCreateRequest {

    /** 规则名（租户内唯一）。 */
    @NotBlank(message = "规则名不能为空")
    @Size(max = 64, message = "规则名最长 64 位")
    @Schema(description = "规则名，租户内唯一", example = "高温告警", maxLength = 64, requiredMode = Schema.RequiredMode.REQUIRED)
    private String ruleName;

    /** 结构化条件 JSON（字段/操作符/阈值；仅声明式比较）。 */
    @NotBlank(message = "规则条件不能为空")
    @Schema(
            description = "结构化条件 JSON（字段/操作符/阈值）；仅支持声明式比较，不支持脚本",
            example = "{\"field\":\"temperature\",\"op\":\">\",\"value\":80}",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String conditionJson;

    /** 动作类型（NOTIFY/WEBHOOK/EVENT）。 */
    @NotBlank(message = "动作类型不能为空")
    @Schema(
            description = "动作类型",
            example = "WEBHOOK",
            allowableValues = {"NOTIFY", "WEBHOOK", "EVENT"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String actionType;

    /** 动作配置 JSON（可空；WEBHOOK 需 connectorKey）。 */
    @Schema(
            description = "动作配置 JSON；actionType=WEBHOOK 时必须提供 connectorKey",
            example = "{\"connectorKey\":\"alarm-hook\"}")
    private String actionConfig;

    /** 是否启用（默认 true）。 */
    @Schema(description = "是否启用，缺省 true", example = "true", defaultValue = "true")
    private Boolean enabled;
}
