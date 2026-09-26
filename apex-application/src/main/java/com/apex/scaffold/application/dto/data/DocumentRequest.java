package com.apex.scaffold.application.dto.data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档创建/更新请求体。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文档创建/更新请求")
public class DocumentRequest {

    @Schema(description = "文档标题", example = "2024年度财务报告")
    @NotBlank(message = "文档标题不能为空")
    @Size(max = 200, message = "文档标题长度不能超过200")
    private String title;

    @Schema(description = "文档类型", example = "report")
    @NotBlank(message = "文档类型不能为空")
    private String docType;

    @Schema(description = "文档分类", example = "finance")
    @NotBlank(message = "文档分类不能为空")
    private String category;

    @Schema(description = "文档内容")
    private String content;

    @Schema(description = "文件URL")
    private String fileUrl;

    @Schema(description = "文件大小(字节)")
    private Long fileSize;

    @Schema(description = "标签")
    private List<String> tags;

    @Schema(description = "状态(0=草稿 1=正常 2=归档)", example = "0")
    private Integer status;
}
