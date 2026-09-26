package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流趋势统计 DTO
 *
 * <p>用于管理后台监控页面展示工作流趋势图表，提供每日流程发起数量和完成数量的时间序列数据。
 * 支持按时间范围统计，帮助管理员分析流程处理效率和业务量变化趋势。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>监控大盘：展示近 7 天或 30 天的流程趋势</li>
 *   <li>效率分析：对比发起数和完成数，发现积压问题</li>
 *   <li>容量规划：根据历史数据预测未来负载</li>
 * </ul>
 *
 * <h2>数据结构</h2>
 * <p>三个列表的索引一一对应，例如：
 * <ul>
 *   <li>dates[0] = "2026-09-01"</li>
 *   <li>startedCounts[0] = 120（当天发起 120 个流程）</li>
 *   <li>completedCounts[0] = 115（当天完成 115 个流程）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工作流趋势统计DTO，提供每日流程发起数量和完成数量的时间序列，用于监控页趋势图表")
public class WorkflowTrendDTO {

    /**
     * 日期列表
     *
     * <p>统计时间范围内的每一天，格式为 {@code yyyy-MM-dd}。
     * 日期按升序排列，通常从最早日期到最近日期。
     *
     * <p>示例：{@code ["2026-09-01", "2026-09-02", "2026-09-03"]}
     */
    @Schema(description = "日期列表，格式 yyyy-MM-dd，按升序排列，与后续两个数量列表索引一一对应")
    private List<String> dates;

    /**
     * 每日发起数量
     *
     * <p>与 {@code dates} 列表索引对应，表示每日新发起的流程实例数量。
     * 统计口径为该日期 00:00:00 到 23:59:59 期间创建的流程。
     *
     * <p>示例：{@code [120, 135, 128]}（三天分别发起 120、135、128 个流程）
     */
    @Schema(description = "每日发起数量列表，与 dates 索引对应")
    private List<Integer> startedCounts;

    /**
     * 每日完成数量
     *
     * <p>与 {@code dates} 列表索引对应，表示每日完成的流程实例数量。
     * 统计口径为该日期 00:00:00 到 23:59:59 期间状态变为"已完成"的流程。
     *
     * <p>示例：{@code [115, 140, 130]}（三天分别完成 115、140、130 个流程）
     *
     * <p>注意：完成数量可能大于发起数量，因为可能完成的是之前发起的流程
     */
    @Schema(description = "每日完成数量列表，与 dates 索引对应，完成数可能大于发起数（含以前发起的流程）")
    private List<Integer> completedCounts;

    /**
     * 获取发起数量列表（别名方法）
     *
     * <p>为兼容前端字段命名提供的别名方法。
     *
     * @return 每日发起数量列表
     */
    public List<Integer> getStarted() {
        return startedCounts;
    }

    /**
     * 设置发起数量列表（别名方法）
     *
     * <p>为兼容前端字段命名提供的别名方法。
     *
     * @param started 每日发起数量列表
     */
    public void setStarted(List<Integer> started) {
        this.startedCounts = started;
    }

    /**
     * 获取完成数量列表（别名方法）
     *
     * <p>为兼容前端字段命名提供的别名方法。
     *
     * @return 每日完成数量列表
     */
    public List<Integer> getCompleted() {
        return completedCounts;
    }

    /**
     * 设置完成数量列表（别名方法）
     *
     * <p>为兼容前端字段命名提供的别名方法。
     *
     * @param completed 每日完成数量列表
     */
    public void setCompleted(List<Integer> completed) {
        this.completedCounts = completed;
    }
}
