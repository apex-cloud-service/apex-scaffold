package com.apex.scaffold.knowledge.application.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 知识文档详情 DTO（含分块预览）。
 *
 * @param document 文档基础信息
 * @param chunks 分块列表（按 seq 升序）
 * @author Apex Team
 * @since 1.3.2
 */
@Schema(name = "KbDocumentDetail", description = "知识文档详情（含分块预览）")
public record KnowledgeDocumentDetailDTO(
        @Schema(description = "文档基础信息", requiredMode = Schema.RequiredMode.REQUIRED) KnowledgeDocumentDTO document,
        @ArraySchema(
                        arraySchema = @Schema(description = "分块列表，按 seq 升序；文档尚未解析时为空数组"),
                        schema = @Schema(implementation = KnowledgeChunkDTO.class))
                List<KnowledgeChunkDTO> chunks) {}
