package com.apex.scaffold.knowledge.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 知识文档响应 DTO。
 *
 * @param id 文档 ID
 * @param spaceId 所属空间 ID
 * @param title 文档标题
 * @param sourceType 来源类型（UPLOAD/MANUAL）
 * @param contentType 内容类型
 * @param fileSize 对象大小（字节）
 * @param parseStatus 解析状态
 * @param parseError 解析失败原因
 * @param chunkCount 分块数量
 * @param indexStatus 索引状态
 * @param createTime 创建时间
 * @param updateTime 更新时间
 * @author Apex Team
 * @since 1.3.2
 */
@Schema(name = "KbDocument", description = "知识文档响应")
public record KnowledgeDocumentDTO(
        @Schema(description = "文档 ID", example = "1", type = "integer", format = "int64") Long id,
        @Schema(description = "所属空间 ID", example = "1", type = "integer", format = "int64") Long spaceId,
        @Schema(description = "文档标题", example = "产品需求说明书.docx", requiredMode = Schema.RequiredMode.REQUIRED)
                String title,
        @Schema(
                        description = "来源类型：UPLOAD=文件上传，MANUAL=在线录入",
                        example = "UPLOAD",
                        allowableValues = {"UPLOAD", "MANUAL"})
                String sourceType,
        @Schema(description = "内容类型（MIME）", example = "application/pdf") String contentType,
        @Schema(description = "对象大小（字节）", example = "524288", type = "integer", format = "int64") Long fileSize,
        @Schema(
                        description = "解析状态，由摄取流程推进；失败原因见 parseError",
                        example = "SUCCESS",
                        allowableValues = {"PENDING", "PROCESSING", "SUCCESS", "FAILED"})
                String parseStatus,
        @Schema(description = "解析失败原因；解析成功时为 null", example = "无法识别的文件格式") String parseError,
        @Schema(description = "已生成的分块数量", example = "42", type = "integer", format = "int32") Integer chunkCount,
        @Schema(
                        description = "索引状态，向量化能力未启用时可能保持未索引",
                        example = "INDEXED",
                        allowableValues = {"PENDING", "INDEXED", "FAILED", "SKIPPED"})
                String indexStatus,
        @Schema(description = "创建时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
                LocalDateTime createTime,
        @Schema(description = "更新时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
                LocalDateTime updateTime) {}
