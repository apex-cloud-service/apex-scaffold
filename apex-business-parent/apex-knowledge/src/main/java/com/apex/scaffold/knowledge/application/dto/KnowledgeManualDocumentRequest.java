package com.apex.scaffold.knowledge.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 在线录入文档请求（MANUAL 来源，不经对象存储）。
 *
 * @param spaceId 所属空间 ID
 * @param title 文档标题
 * @param content 纯文本正文
 * @author Apex Team
 * @since 1.3.2
 */
@Schema(name = "KbManualDocumentRequest", description = "在线录入文档请求（MANUAL 来源，不经对象存储）")
public record KnowledgeManualDocumentRequest(
        @NotNull(message = "空间 ID 不能为空")
                @Schema(
                        description = "所属空间 ID",
                        example = "1",
                        type = "integer",
                        format = "int64",
                        minimum = "1",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long spaceId,
        @NotBlank(message = "文档标题不能为空")
                @Size(max = 200, message = "文档标题最长 200 字符")
                @Schema(
                        description = "文档标题，不能为空白，最长 200 字符",
                        example = "产品需求说明书",
                        maxLength = 200,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String title,
        @NotBlank(message = "文档内容不能为空")
                @Size(max = 400000, message = "文档内容过长")
                @Schema(
                        description = "纯文本正文，不能为空白，最长 400000 字符",
                        example = "本产品面向企业知识管理场景……",
                        maxLength = 400000,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String content) {}
