package com.apex.scaffold.knowledge.application.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识问答消息 DTO。
 *
 * @param id 消息 ID
 * @param conversationId 会话 ID
 * @param role 角色（USER/ASSISTANT）
 * @param content 消息正文
 * @param citations 引用溯源（回答消息专用，可为空列表）
 * @param createTime 创建时间
 * @author Apex Team
 * @since 1.3.2
 */
@Schema(name = "KbMessage", description = "知识问答消息；引用溯源仅回答消息返回")
public record KbMessageDTO(
        @Schema(description = "消息 ID", example = "1", type = "integer", format = "int64") Long id,
        @Schema(description = "会话 ID", example = "1", type = "integer", format = "int64") Long conversationId,
        @Schema(
                        description = "角色：USER=用户提问，ASSISTANT=助手回答",
                        example = "ASSISTANT",
                        allowableValues = {"USER", "ASSISTANT"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String role,
        @Schema(description = "消息正文", example = "退款流程分为申请、审核与退款三步……") String content,
        @ArraySchema(
                        arraySchema = @Schema(description = "引用溯源条目；仅回答消息返回，用户消息为空数组"),
                        schema = @Schema(implementation = CitationDTO.class))
                List<CitationDTO> citations,
        @Schema(description = "创建时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
                LocalDateTime createTime) {

    /**
     * 引用溯源条目。
     *
     * @param index 引用序号（从 1 开始）
     * @param chunkId 分块 ID
     * @param documentId 文档 ID
     * @param documentTitle 文档标题
     * @param spaceId 空间 ID
     * @param snippet 命中片段
     */
    @Schema(name = "KbCitation", description = "回答引用的溯源条目")
    public record CitationDTO(
            @Schema(description = "引用序号，从 1 开始", example = "1", type = "integer", format = "int32") int index,
            @Schema(description = "分块 ID", example = "1001", type = "integer", format = "int64") Long chunkId,
            @Schema(description = "文档 ID", example = "1", type = "integer", format = "int64") Long documentId,
            @Schema(description = "文档标题", example = "产品需求说明书.docx") String documentTitle,
            @Schema(description = "空间 ID", example = "1", type = "integer", format = "int64") Long spaceId,
            @Schema(description = "命中片段", example = "退款流程分为申请、审核与退款三步") String snippet) {}
}
