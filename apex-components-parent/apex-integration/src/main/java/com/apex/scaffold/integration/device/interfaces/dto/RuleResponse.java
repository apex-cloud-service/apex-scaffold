package com.apex.scaffold.integration.device.interfaces.dto;

import com.apex.scaffold.integration.device.domain.DeviceRule;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 规则响应（方案 M5 / 批次 E3）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@Schema(name = "RuleResponse", description = "设备规则响应")
public class RuleResponse {

    @Schema(description = "规则主键 ID", example = "1", type = "integer", format = "int64")
    private Long id;

    @Schema(description = "所属租户 ID", example = "default")
    private String tenantId;

    @Schema(description = "规则名", example = "高温告警")
    private String ruleName;

    @Schema(description = "结构化条件 JSON", example = "{\"field\":\"temperature\",\"op\":\">\",\"value\":80}")
    private String conditionJson;

    @Schema(
            description = "动作类型",
            example = "WEBHOOK",
            allowableValues = {"NOTIFY", "WEBHOOK", "EVENT"})
    private String actionType;

    @Schema(description = "动作配置 JSON", example = "{\"connectorKey\":\"alarm-hook\"}")
    private String actionConfig;

    @Schema(description = "是否启用", example = "true")
    private boolean enabled;

    /**
     * 从规则实体映射。
     *
     * @param rule 规则实体
     * @return 响应
     */
    public static RuleResponse from(DeviceRule rule) {
        RuleResponse response = new RuleResponse();
        response.setId(rule.getId());
        response.setTenantId(rule.getTenantId());
        response.setRuleName(rule.getRuleName());
        response.setConditionJson(rule.getConditionJson());
        response.setActionType(rule.getActionType());
        response.setActionConfig(rule.getActionConfig());
        response.setEnabled(rule.isEnabled());
        return response;
    }
}
