package com.apex.scaffold.application.dto.usage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 能力调用用量 DTO（方案 D4 计量）。
 *
 * <p>租户 × 能力维度的接口调用次数统计行，只读输出，不做计费。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CapabilityUsageDTO {

    /** 租户标识。 */
    private String tenantId;

    /** 能力编码（与网关路由 metadata open-api.capability 口径一致）。 */
    private String capability;

    /** 统计区间内的调用次数。 */
    private Long callCount;
}
