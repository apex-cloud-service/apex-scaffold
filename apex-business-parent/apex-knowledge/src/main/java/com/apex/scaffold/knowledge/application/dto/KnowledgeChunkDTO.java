package com.apex.scaffold.knowledge.application.dto;

/**
 * 知识分块预览 DTO（管理端诊断用，不含向量）。
 *
 * @param id 分块 ID
 * @param seq 序号
 * @param content 分块正文
 * @param tokenEstimate 预估 token 数
 * @author Apex Team
 * @since 1.3.2
 */
public record KnowledgeChunkDTO(Long id, Integer seq, String content, Integer tokenEstimate) {}
