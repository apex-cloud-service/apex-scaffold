package com.apex.scaffold.knowledge.application.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 知识检索请求。
 *
 * @param keyword 检索关键词
 * @param spaceIds 限定空间（可空表示租户内全部正常空间）
 * @param page 页码（从 1 开始）
 * @param size 页大小
 * @author Apex Team
 * @since 1.3.2
 */
@Schema(name = "KbSearchRequest", description = "知识检索请求；词法检索与向量语义检索按能力开关混合执行")
public record KbSearchRequest(
        @NotBlank(message = "检索关键词不能为空")
                @Schema(description = "检索关键词，不能为空白", example = "退款流程", requiredMode = Schema.RequiredMode.REQUIRED)
                String keyword,
        @ArraySchema(
                        arraySchema = @Schema(description = "限定检索的空间 ID 列表；为空表示租户内全部正常空间"),
                        schema = @Schema(type = "integer", format = "int64"))
                List<Long> spaceIds,
        @Schema(
                        description = "页码，从 1 开始，缺省 1",
                        example = "1",
                        type = "integer",
                        format = "int32",
                        minimum = "1",
                        defaultValue = "1")
                Integer page,
        @Schema(
                        description = "页大小，缺省 20，上限 100",
                        example = "20",
                        type = "integer",
                        format = "int32",
                        minimum = "1",
                        maximum = "100",
                        defaultValue = "20")
                Integer size) {}
