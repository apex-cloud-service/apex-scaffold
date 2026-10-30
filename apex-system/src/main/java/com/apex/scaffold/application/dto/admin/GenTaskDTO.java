package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成任务进度响应。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.5.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成任务进度响应")
public class GenTaskDTO {

    private Long id;

    private Long configId;

    private Long historyId;

    private String status;

    private Integer totalCount;

    private Integer processedCount;

    private Integer failedCount;

    private Long operatorId;

    private String errorMessage;

    private Integer attempts;

    private LocalDateTime completedAt;

    private LocalDateTime canceledAt;

    private LocalDateTime createTime;

    /**
     * 进度百分比。
     *
     * <p>总量未知或为 0 时返回 0，避免除零；结果向下取整，不因四舍五入提前显示 100%。
     *
     * @return 0 到 100 之间的整数百分比
     */
    public int getProgressPercent() {
        if (totalCount == null || totalCount <= 0 || processedCount == null || processedCount <= 0) {
            return 0;
        }
        int percent = processedCount * 100 / totalCount;
        return Math.min(percent, 100);
    }
}
