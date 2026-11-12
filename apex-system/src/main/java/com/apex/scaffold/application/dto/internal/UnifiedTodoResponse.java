package com.apex.scaffold.application.dto.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 统一待办聚合响应（S4-a）。
 *
 * <p>{@code GET /api/v2/todo} 的响应负载：按数据源维度返回当前用户待办条目，
 * 单源不可达时该源以 {@code DEGRADED} 呈现（结果缺失 + 错误摘要），整体标记
 * {@code degraded=true}，不影响其他可用数据源。
 *
 * <p>条目为用户个人数据：响应不缓存，每次请求实时聚合（与概览门面的租户级缓存
 * 契约相反——概览端点为租户级聚合数据才允许按租户共享缓存）。
 *
 * @param userId      当前用户 ID（system 业务用户主键）
 * @param retrievedAt 聚合生成时间
 * @param degraded    是否存在降级数据源
 * @param sources     数据源名称 → 单源结果（workflow/office/notifications）
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Schema(name = "UnifiedTodoResponse", description = "统一待办聚合响应；单源降级不影响其他数据源")
public record UnifiedTodoResponse(
        @Schema(description = "当前用户 ID", example = "2001") Long userId,
        @Schema(description = "聚合生成时间") LocalDateTime retrievedAt,
        @Schema(description = "是否存在降级数据源") boolean degraded,
        @Schema(description = "数据源名称 → 单源结果（workflow/office/notifications）") Map<String, SourceSection> sources) {

    /**
     * 单数据源结果。
     *
     * @param status {@code UP}（成功）或 {@code DEGRADED}（超时/不可达/解析失败/舱壁饱和）
     * @param error  降级原因摘要；成功时为 {@code null}
     * @param total  条目总数（截断前的真实数量；降级时为 {@code null}）
     * @param items  待办条目；降级时为 {@code null}
     */
    @Schema(name = "TodoSourceSection", description = "单数据源待办结果")
    public record SourceSection(
            @Schema(description = "状态：UP-成功，DEGRADED-超时/不可达/解析失败", example = "UP") String status,
            @Schema(description = "降级原因摘要；成功时为 null") String error,
            @Schema(description = "条目总数（截断前真实数量）；降级时为 null") Long total,
            @Schema(description = "待办条目；降级时为 null") List<TodoItem> items) {

        /** 单源成功结果。 */
        public static SourceSection up(Long total, List<TodoItem> items) {
            return new SourceSection("UP", null, total, items);
        }

        /** 单源降级结果。 */
        public static SourceSection degraded(String error) {
            return new SourceSection("DEGRADED", error, null, null);
        }
    }

    /**
     * 统一待办条目（对外契约）。
     *
     * @param source     来源域标识：workflow / office / notifications
     * @param type       条目类型：workflow_task / office_approval / office_message / notification
     * @param title      展示标题
     * @param link       跳转链接，预留可空；前端按（来源、类型、refId）自行路由到归属域页面
     * @param occurredAt 发生时间
     * @param status     条目状态：PENDING / UNREAD
     * @param refId      归属域内业务标识
     */
    @Schema(name = "TodoItem", description = "统一待办条目")
    public record TodoItem(
            @Schema(description = "来源域标识", example = "workflow") String source,
            @Schema(description = "条目类型", example = "workflow_task") String type,
            @Schema(description = "展示标题", example = "部门经理审批") String title,
            @Schema(description = "跳转链接，预留可空") String link,
            @Schema(description = "发生时间") LocalDateTime occurredAt,
            @Schema(description = "条目状态", example = "PENDING") String status,
            @Schema(description = "归属域内业务标识", example = "5001") String refId) {}
}
