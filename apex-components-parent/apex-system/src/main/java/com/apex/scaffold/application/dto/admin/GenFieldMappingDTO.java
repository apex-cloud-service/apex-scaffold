package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成字段映射请求 DTO
 *
 * <p>允许向导对单个表的字段做局部覆盖，例如修正列注释、覆盖 Java 字段名或类型。
 * 未在 {@code columns} 中出现的列沿用元数据读取结果，不做修改。
 *
 * <p>映射只影响渲染数据模型，不会修改源数据库结构。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成字段映射")
public class GenFieldMappingDTO {

    @Schema(description = "表名", example = "sys_user")
    private String tableName;

    @Schema(description = "字段覆盖列表，未列出的字段沿用元数据")
    private List<ColumnMapping> columns;

    /**
     * 单列映射覆盖项。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "单列映射覆盖")
    public static class ColumnMapping {

        @Schema(description = "列名", example = "user_name")
        private String columnName;

        @Schema(description = "覆盖后的 Java 字段名", example = "userName")
        private String javaFieldName;

        @Schema(description = "覆盖后的 Java 类型", example = "String")
        private String javaType;

        @Schema(description = "覆盖后的字段注释", example = "登录名")
        private String columnComment;

        @Schema(description = "是否忽略该列（不参与渲染）", example = "false")
        private Boolean ignored;
    }
}
