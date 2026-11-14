package com.apex.scaffold.integration.device.interfaces.dto;

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
public class RuleCreateRequest {

    /** 规则名（租户内唯一）。 */
    @NotBlank(message = "规则名不能为空")
    @Size(max = 64, message = "规则名最长 64 位")
    private String ruleName;

    /** 结构化条件 JSON（字段/操作符/阈值；仅声明式比较）。 */
    @NotBlank(message = "规则条件不能为空")
    private String conditionJson;

    /** 动作类型（NOTIFY/WEBHOOK/EVENT）。 */
    @NotBlank(message = "动作类型不能为空")
    private String actionType;

    /** 动作配置 JSON（可空；WEBHOOK 需 connectorKey）。 */
    private String actionConfig;

    /** 是否启用（默认 true）。 */
    private Boolean enabled;
}
