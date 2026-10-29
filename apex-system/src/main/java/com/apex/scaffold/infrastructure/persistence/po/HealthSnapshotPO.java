package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 运维体检历史快照持久化对象。
 *
 * <p>对应数据库表 {@code ops_health_snapshot}，保存管理员落库的体检结论。
 * 仅固化总体结论、状态计数与摘要，不保存逐项证据，避免平台实例细节随历史扩散。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.domain.model.monitor.HealthSnapshotEntity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("ops_health_snapshot")
public class HealthSnapshotPO extends BasePO {

    /** 采集来源（MANUAL/SCHEDULED）。 */
    private String captureSource;

    /** 采集时的服务名。 */
    private String serviceName;

    /** 采集时的实例标识（PID@主机名）。 */
    private String instanceId;

    /** 采集范围，固定为 INSTANCE。 */
    private String scope;

    /** 判定规则版本。 */
    private String ruleVersion;

    /** 总体结论（UP/DEGRADED/DOWN/UNKNOWN）。 */
    private String overallStatus;

    /** 总体结论说明。 */
    private String summary;

    /** UP 检查项数。 */
    private Integer upCount;

    /** DEGRADED 检查项数。 */
    private Integer degradedCount;

    /** DOWN 检查项数。 */
    private Integer downCount;

    /** UNKNOWN 检查项数。 */
    private Integer unknownCount;

    /** NOT_CONFIGURED 检查项数。 */
    private Integer notConfiguredCount;

    /** 报告生成时间（采集时刻）。 */
    private LocalDateTime generatedAt;

    /** 租户标识。 */
    private String tenantId;
}
