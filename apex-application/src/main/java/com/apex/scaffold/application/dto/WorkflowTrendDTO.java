package com.apex.scaffold.application.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流趋势 DTO。
 *
 * <p>用于监控页面展示流程趋势图（每日发起/完成数量）。</p>
 *
 * @author Apex Team
 * @version 1.4.0
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTrendDTO {

    /** 日期列表（如 2026-08-27） */
    private List<String> dates;

    /** 每日发起数量 */
    private List<Integer> startedCounts;

    /** 每日完成数量 */
    private List<Integer> completedCounts;

    public List<Integer> getStarted() {
        return startedCounts;
    }

    public void setStarted(List<Integer> started) {
        this.startedCounts = started;
    }

    public List<Integer> getCompleted() {
        return completedCounts;
    }

    public void setCompleted(List<Integer> completed) {
        this.completedCounts = completed;
    }
}
