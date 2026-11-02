package com.apex.scaffold.integration.device.interfaces.dto;

import com.apex.scaffold.integration.device.domain.DeviceRule;
import lombok.Data;

/**
 * 规则响应（方案 M5 / 批次 E3）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
public class RuleResponse {

    private Long id;

    private String tenantId;

    private String ruleName;

    private String conditionJson;

    private String actionType;

    private String actionConfig;

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
