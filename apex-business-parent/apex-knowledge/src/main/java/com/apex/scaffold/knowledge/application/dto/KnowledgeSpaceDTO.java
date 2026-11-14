package com.apex.scaffold.knowledge.application.dto;

import java.time.LocalDateTime;

/**
 * 知识空间响应 DTO。
 *
 * @param id 空间 ID
 * @param name 空间名称
 * @param description 空间描述
 * @param status 状态（0=停用，1=正常）
 * @param createTime 创建时间
 * @param updateTime 更新时间
 * @author Apex Team
 * @since 1.3.2
 */
public record KnowledgeSpaceDTO(
        Long id, String name, String description, Integer status, LocalDateTime createTime, LocalDateTime updateTime) {}
