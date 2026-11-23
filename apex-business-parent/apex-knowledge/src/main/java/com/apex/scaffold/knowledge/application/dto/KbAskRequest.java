package com.apex.scaffold.knowledge.application.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 知识问答请求（SSE 流式应答）。
 *
 * @param conversationId 会话 ID（为空时新建会话）
 * @param spaceIds 限定检索空间（可空表示租户内全部正常空间）
 * @param question 用户提问
 * @author Apex Team
 * @since 1.3.2
 */
@Schema(name = "KbAskRequest", description = "知识问答请求；响应为 text/event-stream 流式事件")
public record KbAskRequest(
        @Schema(description = "会话 ID；为空时新建会话", example = "1", type = "integer", format = "int64", minimum = "1")
                Long conversationId,
        @ArraySchema(
                        arraySchema = @Schema(description = "限定检索的空间 ID 列表；为空表示租户内全部正常空间"),
                        schema = @Schema(type = "integer", format = "int64"))
                List<Long> spaceIds,
        @NotBlank(message = "提问内容不能为空")
                @Schema(
                        description = "用户提问内容，不能为空白",
                        example = "本产品的退款流程是怎样的？",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String question) {}
