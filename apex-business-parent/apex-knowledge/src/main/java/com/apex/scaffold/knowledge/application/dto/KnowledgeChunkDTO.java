package com.apex.scaffold.knowledge.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(name = "KbChunk", description = "知识分块预览（管理端诊断用，不含向量）")
public record KnowledgeChunkDTO(
        @Schema(description = "分块 ID", example = "1", type = "integer", format = "int64") Long id,
        @Schema(description = "分块序号，按升序排列", example = "1", type = "integer", format = "int32") Integer seq,
        @Schema(description = "分块正文", example = "本产品面向企业知识管理场景……", requiredMode = Schema.RequiredMode.REQUIRED)
                String content,
        @Schema(description = "预估 token 数", example = "256", type = "integer", format = "int32")
                Integer tokenEstimate) {}
