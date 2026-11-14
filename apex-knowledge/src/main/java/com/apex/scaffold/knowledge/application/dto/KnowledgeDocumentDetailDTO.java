package com.apex.scaffold.knowledge.application.dto;

import java.util.List;

/**
 * 知识文档详情 DTO（含分块预览）。
 *
 * @param document 文档基础信息
 * @param chunks 分块列表（按 seq 升序）
 * @author Apex Team
 * @since 1.3.2
 */
public record KnowledgeDocumentDetailDTO(KnowledgeDocumentDTO document, List<KnowledgeChunkDTO> chunks) {}
