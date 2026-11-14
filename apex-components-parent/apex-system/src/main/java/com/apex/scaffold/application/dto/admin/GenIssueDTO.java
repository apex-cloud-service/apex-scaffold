package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成向导结构化问题项 DTO
 *
 * <p>预检与执行阶段以结构化方式返回阻塞项与告警项，前端据此定位到配置、数据源、表或模板字段，
 * 不再依赖解析自由文本错误消息。
 *
 * <p>级别语义：
 * <ul>
 *   <li><strong>ERROR</strong>：阻塞项，存在时 {@code ready=false}，不允许执行</li>
 *   <li><strong>WARN</strong>：告警项，不阻塞执行，但需要用户确认</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成向导问题项")
public class GenIssueDTO {

    @Schema(description = "问题级别（ERROR/WARN）", example = "ERROR")
    private String level;

    @Schema(description = "问题代码（稳定标识，供前端映射文案）", example = "DATASOURCE_TYPE_UNSUPPORTED")
    private String code;

    @Schema(description = "问题定位字段", example = "datasourceId")
    private String field;

    @Schema(description = "问题描述", example = "当前仅支持 MySQL 与 PostgreSQL 数据源")
    private String message;
}
