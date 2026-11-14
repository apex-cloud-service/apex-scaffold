package com.apex.scaffold.knowledge.application.dto;

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
public record KnowledgeManualDocumentRequest(
        @NotNull(message = "空间 ID 不能为空") Long spaceId,
        @NotBlank(message = "文档标题不能为空") @Size(max = 200, message = "文档标题最长 200 字符") String title,
        @NotBlank(message = "文档内容不能为空") @Size(max = 400000, message = "文档内容过长") String content) {}
