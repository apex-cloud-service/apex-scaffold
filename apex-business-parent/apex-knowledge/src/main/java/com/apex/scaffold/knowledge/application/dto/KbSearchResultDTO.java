package com.apex.scaffold.knowledge.application.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "KbSearchResult", description = "知识检索响应；hybridUsed 反映本次是否真正启用向量语义检索")
public record KbSearchResultDTO(
        @Schema(
                        description = "命中总数（未受分页截断前的总条数）",
                        example = "37",
                        type = "integer",
                        format = "int64",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long total,
        @Schema(
                        description = "是否实际启用向量语义检索；false 表示仅词法结果或向量能力未启用",
                        example = "true",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                boolean hybridUsed,
        @ArraySchema(
                        arraySchema = @Schema(description = "当前页命中条目"),
                        schema = @Schema(implementation = KbSearchItemDTO.class))
                List<KbSearchItemDTO> items) {

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
    @Schema(name = "KbSearchItem", description = "检索命中条目")
    public record KbSearchItemDTO(
            @Schema(description = "分块 ID", example = "1001", type = "integer", format = "int64") Long chunkId,
            @Schema(description = "文档 ID", example = "1", type = "integer", format = "int64") Long documentId,
            @Schema(description = "文档标题", example = "产品需求说明书.docx") String documentTitle,
            @Schema(description = "空间 ID", example = "1", type = "integer", format = "int64") Long spaceId,
            @Schema(description = "空间名称", example = "产品知识库") String spaceName,
            @Schema(description = "分块正文", example = "退款流程分为申请、审核与退款三步……") String content,
            @Schema(description = "高亮片段；未命中高亮时为 null", example = "<em>退款流程</em>分为申请、审核与退款三步") String highlight,
            @Schema(description = "相关度得分，越大越相关；词法与向量混合得分", example = "0.87", type = "number", format = "double")
                    double score) {}
}
