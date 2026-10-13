package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成文件结果 DTO
 *
 * <p>代码生成操作的结果，包含生成的文件路径和内容摘要。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成文件结果")
public class GeneratedFileDTO {

    @Schema(description = "文件类型", example = "Entity")
    private String fileType;

    @Schema(description = "文件路径", example = "/Users/work/apex-scaffold/output/domain/model/entity/UserEntity.java")
    private String filePath;

    @Schema(description = "文件名", example = "UserEntity.java")
    private String fileName;

    @Schema(description = "文件大小（字节）", example = "2048")
    private Long fileSize;

    @Schema(description = "是否成功生成", example = "true")
    private Boolean success;

    @Schema(description = "错误信息（如果失败）", example = "模板解析失败")
    private String errorMessage;
}
