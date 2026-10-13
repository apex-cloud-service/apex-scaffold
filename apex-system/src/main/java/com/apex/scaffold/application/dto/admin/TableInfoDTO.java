package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库表信息 DTO
 *
 * <p>从数据库元数据读取的表信息，包含表名、注释和列信息列表。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据库表信息")
public class TableInfoDTO {

    @Schema(description = "表名", example = "sys_user")
    private String tableName;

    @Schema(description = "表注释", example = "系统用户表")
    private String tableComment;

    @Schema(description = "表类型", example = "TABLE")
    private String tableType;

    @Schema(description = "列信息列表")
    private List<ColumnInfoDTO> columns;
}
