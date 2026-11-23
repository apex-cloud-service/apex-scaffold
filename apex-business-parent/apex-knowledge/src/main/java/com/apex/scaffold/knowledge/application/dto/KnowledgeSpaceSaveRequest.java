package com.apex.scaffold.knowledge.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 知识空间创建/更新请求。
 *
 * <p>{@code @NotBlank} 仅校验非空白，不修剪输入；名称长度上限 64、描述 512。
 *
 * @param name 空间名称
 * @param description 空间描述
 * @param status 状态（0=停用，1=正常；更新时生效）
 * @author Apex Team
 * @since 1.3.2
 */
@Schema(name = "KbSpaceSaveRequest", description = "知识空间创建/更新请求")
public record KnowledgeSpaceSaveRequest(
        @NotBlank(message = "空间名称不能为空")
                @Size(max = 64, message = "空间名称最长 64 字符")
                @Schema(
                        description = "空间名称，不能为空白，最长 64 字符",
                        example = "产品知识库",
                        maxLength = 64,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String name,
        @Size(max = 512, message = "空间描述最长 512 字符")
                @Schema(description = "空间描述，可空，最长 512 字符", example = "存放产品需求与设计文档", maxLength = 512)
                String description,
        @Schema(
                        description = "状态：0=停用，1=正常；更新时生效，创建时缺省为 1",
                        example = "1",
                        allowableValues = {"0", "1"},
                        type = "integer",
                        format = "int32")
                Integer status) {}
