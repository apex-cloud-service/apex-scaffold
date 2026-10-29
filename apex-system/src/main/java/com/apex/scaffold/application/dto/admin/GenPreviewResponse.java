package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成向导预览结果 DTO
 *
 * <p>纯渲染结果，仅返回内容摘要与片段，不回传完整文件内容，也不写入服务器文件系统。
 * {@code previewToken} 绑定配置版本、模板内容摘要、字段映射与目标表集合，
 * 执行时必须原样带回，输入摘要变化后旧令牌失效，需要重新预览。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成向导预览结果")
public class GenPreviewResponse {

    @Schema(description = "预览令牌（执行时原样提交）", example = "c2f1…")
    private String previewToken;

    @Schema(description = "输入摘要（SHA-256 前 16 位）", example = "9ab3…")
    private String inputDigest;

    @Schema(description = "是否可执行（无 ERROR 级问题）", example = "true")
    private Boolean ready;

    @Schema(description = "结构化问题项")
    private List<GenIssueDTO> issues;

    @Schema(description = "预览文件列表")
    private List<GenPreviewFileDTO> files;

    /**
     * 单个预览文件。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "预览文件")
    public static class GenPreviewFileDTO {

        @Schema(description = "表名", example = "sys_user")
        private String tableName;

        @Schema(description = "模板 ID", example = "1001")
        private Long templateId;

        @Schema(description = "模板类型", example = "ENTITY")
        private String templateType;

        @Schema(description = "相对输出路径", example = "domain/model/UserEntity.java")
        private String relativePath;

        @Schema(description = "渲染内容大小（字节）", example = "2048")
        private Integer contentSize;

        @Schema(description = "渲染内容行数", example = "65")
        private Integer lineCount;

        @Schema(description = "内容 SHA-256 摘要（前 16 位）", example = "1c9d…")
        private String contentDigest;

        @Schema(description = "内容片段（最多 4000 字符）")
        private String contentPreview;

        @Schema(description = "是否被截断", example = "false")
        private Boolean truncated;
    }
}
