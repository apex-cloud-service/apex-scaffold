package com.apex.scaffold.domain.model.monitor;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 运维体检历史快照实体（system 领域模型）。
 *
 * <p>固化管理员在某一次采集中得到的体检结论，用于回答「昨天是否也这样」「风险是否在收敛」。
 * 快照只保存总体结论、状态计数与摘要文本，不复制逐项 {@code source}/{@code evidence}，
 * 避免平台级实例证据随快照扩散到历史归档。
 *
 * <h2>关键约束</h2>
 * <ul>
 *   <li>{@link #scope} 固定为 {@code INSTANCE}：快照只代表单实例进程内采集结果，不代表集群</li>
 *   <li>{@link #ruleVersion} 记录判定规则版本，跨版本趋势比较前需先确认口径一致</li>
 *   <li>{@link #generatedAt} 是报告生成时刻，与 {@code createTime}（落库时刻）可能不同</li>
 *   <li>{@link #tenantId} 由可信认证上下文写入，读写均由租户拦截器追加过滤</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class HealthSnapshotEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 采集来源：管理员手工触发。 */
    public static final String CAPTURE_SOURCE_MANUAL = "MANUAL";

    /** 采集来源：定时任务触发。 */
    public static final String CAPTURE_SOURCE_SCHEDULED = "SCHEDULED";

    /** 采集范围固定值：仅当前实例。 */
    public static final String SCOPE_INSTANCE = "INSTANCE";

    /**
     * 创建空快照实体。
     *
     * <p>必须显式初始化时间戳，否则 {@code createTime} 为空，
     * 而持久化层的自动填充只在 PO 上生效。</p>
     */
    public HealthSnapshotEntity() {
        initTimestamps();
    }

    /** 采集来源（MANUAL/SCHEDULED）。 */
    private String captureSource;

    /** 采集时的服务名。 */
    private String serviceName;

    /** 采集时的实例标识（PID@主机名）。 */
    private String instanceId;

    /** 采集范围，固定为 {@link #SCOPE_INSTANCE}。 */
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
