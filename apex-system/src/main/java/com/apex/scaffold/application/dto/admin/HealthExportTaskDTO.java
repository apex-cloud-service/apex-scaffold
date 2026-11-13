package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 体检快照异步导出任务视图对象。
 *
 * <p>只暴露任务进度与结果元数据，文件内容通过独立下载端点获取，
 * 避免列表与轮询接口返回大字段。</p>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "体检快照异步导出任务")
public class HealthExportTaskDTO {

    @Schema(description = "任务ID", example = "1958000000000000001")
    private Long id;

    @Schema(description = "导出条件：生成时间下界", example = "2026-10-01T00:00:00")
    private LocalDateTime queryFrom;

    @Schema(description = "导出条件：生成时间上界", example = "2026-10-29T00:00:00")
    private LocalDateTime queryTo;

    @Schema(description = "导出条件：总体结论过滤", example = "DEGRADED")
    private String queryStatus;

    @Schema(description = "任务状态 PENDING/RUNNING/COMPLETED/FAILED", example = "COMPLETED")
    private String status;

    @Schema(description = "已尝试次数", example = "1")
    private Integer attempts;

    @Schema(description = "最近一次失败原因")
    private String lastError;

    @Schema(description = "生成文件名", example = "health-snapshots-20261029101530.csv")
    private String fileName;

    @Schema(description = "导出记录数", example = "12000")
    private Integer rowCount;

    @Schema(description = "完成时间", example = "2026-10-29T10:15:31")
    private LocalDateTime completedAt;

    @Schema(description = "提交时间", example = "2026-10-29T10:15:30")
    private LocalDateTime createTime;
}
