package com.apex.scaffold.interfaces.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 管理员体检报告响应（GET /api/v2/operations/health-report）。
 *
 * <p>在进程内复用监控采集结果，按「应用存活 / 依赖就绪 / 业务风险 / 容量」四类分组
 * 生成可解释的即时体检结论。每个检查项显式给出状态、采集范围、采样时间、数据来源、
 * 证据与整改建议，避免把「无指标」「组件未启用」「探测超时」误判为健康。</p>
 *
 * <p>报告范围是<b>当前实例</b>：{@code scope} 固定为 {@code INSTANCE}，
 * {@code scopeNotice} 说明结果不能代表整个集群或其他服务。</p>
 *
 * @param generatedAt 报告生成时间（ISO-8601）
 * @param service 服务名
 * @param instance 实例标识
 * @param scope 采集范围（当前固定 INSTANCE）
 * @param scopeNotice 范围说明
 * @param ruleVersion 判定规则版本
 * @param overallStatus 总体结论 UP/DEGRADED/DOWN/UNKNOWN
 * @param summary 总体结论说明
 * @param counts 各状态检查项计数
 * @param groups 分组检查结果
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Schema(description = "管理员体检报告（即时、单实例范围）")
public record HealthReportResponse(
        @Schema(description = "报告生成时间（ISO-8601）") String generatedAt,
        @Schema(description = "服务名") String service,
        @Schema(description = "实例标识（进程 PID@主机名）") String instance,
        @Schema(description = "采集范围，当前固定 INSTANCE") String scope,
        @Schema(description = "范围说明：单实例结果不代表全栈") String scopeNotice,
        @Schema(description = "判定规则版本") String ruleVersion,
        @Schema(description = "总体结论 UP/DEGRADED/DOWN/UNKNOWN") String overallStatus,
        @Schema(description = "总体结论说明") String summary,
        @Schema(description = "各状态检查项计数") StatusCounts counts,
        @Schema(description = "分组检查结果") List<HealthGroup> groups) {

    /** 检查项状态常量。 */
    public static final String STATUS_UP = "UP";
    /** 降级：可服务但存在风险。 */
    public static final String STATUS_DEGRADED = "DEGRADED";
    /** 失败：依赖不可用或明确异常。 */
    public static final String STATUS_DOWN = "DOWN";
    /** 未知：缺指标、采集失败或探测超时，不能计为健康。 */
    public static final String STATUS_UNKNOWN = "UNKNOWN";
    /** 未配置：组件未启用或未接入，不参与总体结论。 */
    public static final String STATUS_NOT_CONFIGURED = "NOT_CONFIGURED";

    /**
     * 状态计数。
     *
     * @param up 正常项数
     * @param degraded 降级项数
     * @param down 失败项数
     * @param unknown 未知项数
     * @param notConfigured 未配置项数
     */
    @Schema(description = "各状态检查项计数")
    public record StatusCounts(
            @Schema(description = "正常项数") int up,
            @Schema(description = "降级项数") int degraded,
            @Schema(description = "失败项数") int down,
            @Schema(description = "未知项数") int unknown,
            @Schema(description = "未配置项数") int notConfigured) {}

    /**
     * 分组检查结果。
     *
     * @param category 分组编码 LIVENESS/DEPENDENCY/BUSINESS_RISK/CAPACITY
     * @param title 分组名称
     * @param status 分组结论（取组内最差状态）
     * @param items 分组检查项
     */
    @Schema(description = "分组检查结果")
    public record HealthGroup(
            @Schema(description = "分组编码 LIVENESS/DEPENDENCY/BUSINESS_RISK/CAPACITY") String category,
            @Schema(description = "分组名称") String title,
            @Schema(description = "分组结论（取组内最差状态）") String status,
            @Schema(description = "分组检查项") List<HealthCheckItem> items) {}

    /**
     * 单项检查结果。
     *
     * @param id 检查项标识
     * @param name 检查项名称
     * @param status 状态 UP/DEGRADED/DOWN/UNKNOWN/NOT_CONFIGURED
     * @param scope 采集范围（INSTANCE）
     * @param service 服务名
     * @param instance 实例标识
     * @param sampledAt 采样时间（ISO-8601）
     * @param source 数据来源（指标名/配置项/Bean）
     * @param evidence 证据（脱敏后的可解释数据）
     * @param recommendation 整改建议；正常时为空
     * @param ruleVersion 判定规则版本
     */
    @Schema(description = "单项检查结果")
    public record HealthCheckItem(
            @Schema(description = "检查项标识") String id,
            @Schema(description = "检查项名称") String name,
            @Schema(description = "状态 UP/DEGRADED/DOWN/UNKNOWN/NOT_CONFIGURED") String status,
            @Schema(description = "采集范围（INSTANCE）") String scope,
            @Schema(description = "服务名") String service,
            @Schema(description = "实例标识") String instance,
            @Schema(description = "采样时间（ISO-8601）") String sampledAt,
            @Schema(description = "数据来源（指标名/配置项/Bean）") String source,
            @Schema(description = "证据（脱敏后的可解释数据）") String evidence,
            @Schema(description = "整改建议；正常时为空") String recommendation,
            @Schema(description = "判定规则版本") String ruleVersion) {}
}
