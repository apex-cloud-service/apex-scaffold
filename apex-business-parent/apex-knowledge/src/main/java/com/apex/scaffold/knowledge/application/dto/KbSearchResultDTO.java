package com.apex.scaffold.knowledge.application.dto;

import java.util.List;

/**
 * 知识检索响应 DTO。
 *
 * @param total 命中总数
 * @param hybridUsed 是否实际启用向量语义检索（false 表示词法结果或未启用）
 * @param items 命中条目
 * @author Apex Team
 * @since 1.3.2
 */
public record KbSearchResultDTO(long total, boolean hybridUsed, List<KbSearchItemDTO> items) {

    /**
     * 检索命中条目。
     *
     * @param chunkId 分块 ID
     * @param documentId 文档 ID
     * @param documentTitle 文档标题
     * @param spaceId 空间 ID
     * @param spaceName 空间名称
     * @param content 分块正文
     * @param highlight 高亮片段（可能为空）
     * @param score 相关度得分
     */
    public record KbSearchItemDTO(
            Long chunkId,
            Long documentId,
            String documentTitle,
            Long spaceId,
            String spaceName,
            String content,
            String highlight,
            double score) {}
}
