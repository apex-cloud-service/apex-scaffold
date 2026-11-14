package com.apex.scaffold.knowledge.application.dto;

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
public record KbConversationDTO(Long id, String title, LocalDateTime createTime, LocalDateTime updateTime) {}
