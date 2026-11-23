package com.apex.scaffold.knowledge.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "KbSpace", description = "知识空间响应")
public record KnowledgeSpaceDTO(
        @Schema(description = "空间 ID", example = "1", type = "integer", format = "int64") Long id,
        @Schema(description = "空间名称", example = "产品知识库", requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "空间描述", example = "存放产品需求与设计文档") String description,
        @Schema(
                        description = "状态：0=停用，1=正常",
                        example = "1",
                        allowableValues = {"0", "1"},
                        type = "integer",
                        format = "int32")
                Integer status,
        @Schema(description = "创建时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
                LocalDateTime createTime,
        @Schema(description = "更新时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
                LocalDateTime updateTime) {}
