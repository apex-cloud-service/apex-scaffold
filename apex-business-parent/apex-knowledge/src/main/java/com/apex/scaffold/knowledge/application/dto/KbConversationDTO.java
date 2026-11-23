package com.apex.scaffold.knowledge.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 知识问答会话 DTO。
 *
 * @param id 会话 ID
 * @param title 会话标题
 * @param createTime 创建时间
 * @param updateTime 更新时间
 * @author Apex Team
 * @since 1.3.2
 */
@Schema(name = "KbConversation", description = "知识问答会话")
public record KbConversationDTO(
        @Schema(description = "会话 ID", example = "1", type = "integer", format = "int64") Long id,
        @Schema(description = "会话标题，通常取自首个提问", example = "退款流程咨询") String title,
        @Schema(description = "创建时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
                LocalDateTime createTime,
        @Schema(description = "更新时间", example = "2026-11-22T10:20:00", type = "string", format = "date-time")
                LocalDateTime updateTime) {}
