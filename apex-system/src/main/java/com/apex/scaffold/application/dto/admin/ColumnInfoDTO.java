package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库列信息 DTO
 *
 * <p>从数据库元数据读取的列信息，包含列名、数据类型、注释等详细信息。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据库列信息")
public class ColumnInfoDTO {

    @Schema(description = "列名", example = "user_name")
    private String columnName;

    @Schema(description = "数据库类型", example = "VARCHAR")
    private String dataType;

    @Schema(description = "Java 类型", example = "String")
    private String javaType;

    @Schema(description = "Java 字段名（驼峰）", example = "userName")
    private String javaFieldName;

    @Schema(description = "列注释", example = "用户名")
    private String columnComment;

    @Schema(description = "是否主键", example = "false")
    private Boolean isPrimaryKey;

    @Schema(description = "是否可空", example = "true")
    private Boolean isNullable;

    @Schema(description = "列长度", example = "100")
    private Integer columnLength;

    @Schema(description = "默认值", example = "NULL")
    private String defaultValue;
}
