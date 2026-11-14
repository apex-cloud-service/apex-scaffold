package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成模板目录项 DTO
 *
 * <p>描述向导可选的模板及其引擎能力、内容版本与内容摘要。{@code engine} 固定为
 * {@code VARIABLE_SUBSTITUTION}，表示当前在线轻量引擎只做 {@code ${variable}} 变量替换，
 * 不支持 FreeMarker 指令（{@code <#if>}、{@code <#list>} 等）；独立 FreeMarker 模板不能与本引擎混用，
 * 需要先转换语法。
 *
 * <p>{@code contentVersion} 与 {@code contentSha256} 用于预览与执行的一致性校验：
 * 预览签发的令牌绑定模板内容摘要，执行时若摘要变化必须重新预览。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码生成模板目录项")
public class GenTemplateCatalogDTO {

    @Schema(description = "模板 ID", example = "1001")
    private Long templateId;

    @Schema(description = "模板名称", example = "实体类模板")
    private String templateName;

    @Schema(description = "模板类型", example = "ENTITY")
    private String templateType;

    @Schema(description = "输出文件后缀", example = ".java")
    private String fileSuffix;

    @Schema(description = "输出相对路径模板", example = "domain/model/${tableName}Entity.java")
    private String templatePath;

    @Schema(description = "渲染引擎", example = "VARIABLE_SUBSTITUTION")
    private String engine;

    @Schema(description = "引擎能力说明", example = "仅支持 ${variable} 变量替换，不支持 FreeMarker 指令")
    private String engineCapabilities;

    @Schema(description = "内容版本（模板更新时间戳，变化即需重新预览）", example = "2026-10-28T10:00:00")
    private String contentVersion;

    @Schema(description = "内容 SHA-256 摘要（前 16 位）", example = "3f2a9c1e7b0d4a56")
    private String contentSha256;

    @Schema(description = "是否内置模板（内置模板只读共享）", example = "true")
    private Boolean builtin;

    @Schema(description = "是否仅可读（内置模板为 true，不可修改删除）", example = "true")
    private Boolean readOnly;
}
