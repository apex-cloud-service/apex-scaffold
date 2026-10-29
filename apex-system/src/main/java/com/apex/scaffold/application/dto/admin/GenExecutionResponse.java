package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成向导执行结果 DTO
 *
 * <p>发布状态语义：
 * <ul>
 *   <li><strong>PUBLISHED</strong>：全部产物写入成功，可作为一次完整发布</li>
 *   <li><strong>PARTIAL</strong>：部分产物写入失败，成功产物保留在任务目录并可按 ID 下载</li>
 *   <li><strong>FAILED</strong>：没有任何产物写入成功</li>
 * </ul>
 *
 * <p>部分失败不会被转换成成功，{@code issues} 会逐项说明失败原因。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成向导执行结果")
public class GenExecutionResponse {

    @Schema(description = "生成任务 ID", example = "1830000000000000001")
    private Long taskId;

    @Schema(description = "生成历史 ID", example = "1")
    private Long historyId;

    @Schema(description = "发布状态（PUBLISHED/PARTIAL/FAILED）", example = "PUBLISHED")
    private String status;

    @Schema(description = "任务输出目录（租户/任务隔离）", example = "tenant-a/1830000000000000001")
    private String taskDirectory;

    @Schema(description = "成功产物列表")
    private List<GenArtifactDTO> artifacts;

    @Schema(description = "结构化问题项（失败原因）")
    private List<GenIssueDTO> issues;
}
