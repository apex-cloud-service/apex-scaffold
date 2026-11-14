package com.apex.scaffold.knowledge.application.dto;

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
public record KbAskRequest(Long conversationId, List<Long> spaceIds, @NotBlank(message = "提问内容不能为空") String question) {}
