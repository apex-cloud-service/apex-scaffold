package com.apex.scaffold.knowledge.application.dto;

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
public record KnowledgeDocumentDTO(
        Long id,
        Long spaceId,
        String title,
        String sourceType,
        String contentType,
        Long fileSize,
        String parseStatus,
        String parseError,
        Integer chunkCount,
        String indexStatus,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
