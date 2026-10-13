package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色创建或更新请求
 *
 * <p>用于创建新角色或更新现有角色信息。角色是 RBAC（基于角色的访问控制）权限模型的核心，
 * 通过将权限分配给角色，再将角色分配给用户，实现灵活的权限管理。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>创建角色：管理员在后台创建新角色（id 为空）</li>
 *   <li>更新角色：管理员修改现有角色信息（id 必填）</li>
 *   <li>角色启用/禁用：通过修改 status 字段控制角色状态</li>
 * </ul>
 *
 * <h2>角色编码规范</h2>
 * <ul>
 *   <li>格式：{@code ROLE_} 前缀 + 大写字母/数字/下划线</li>
 *   <li>示例：{@code ROLE_ADMIN}、{@code ROLE_USER}、{@code ROLE_OPERATOR}</li>
 *   <li>唯一性：同一系统内角色编码必须唯一</li>
 * </ul>
 *
 * <h2>角色状态</h2>
 * <ul>
 *   <li>{@code 0}：禁用，拥有该角色的用户无法使用相关权限</li>
 *   <li>{@code 1}：正常，拥有该角色的用户可以正常使用相关权限</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 创建角色
 * RoleRequest createRequest = RoleRequest.builder()
 *     .name("内容管理员")
 *     .code("ROLE_CONTENT_ADMIN")
 *     .description("负责内容审核和管理")
 *     .status(1)
 *     .build();
 *
 * // 更新角色
 * RoleRequest updateRequest = RoleRequest.builder()
 *     .id(100L)
 *     .name("高级内容管理员")
 *     .code("ROLE_CONTENT_ADMIN")
 *     .description("负责内容审核、管理和策略制定")
 *     .status(1)
 *     .build();
 * }</pre>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see com.apex.scaffold.application.service.RoleService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RoleRequest", description = "角色创建或更新请求")
public class RoleRequest {

    /**
     * 角色主键 ID
     *
     * <p>角色的唯一标识。
     *
     * <p>使用规则：
     * <ul>
     *   <li>创建角色：此字段为空（或不传）</li>
     *   <li>更新角色：此字段必填，指定要更新的角色 ID</li>
     * </ul>
     *
     * <p>示例：{@code 100}
     */
    @Schema(description = "角色ID（创建时为空，更新时必填）", example = "100")
    private Long id;

    /**
     * 角色名称
     *
     * <p>面向管理员的可读名称，用于在管理界面展示。
     * 角色名称应简洁明了，能够清晰表达角色的职责。
     *
     * <p>验证规则：
     * <ul>
     *   <li>必填</li>
     *   <li>建议长度：2-20 个字符</li>
     * </ul>
     *
     * <p>示例：{@code 超级管理员}、{@code 内容管理员}、{@code 普通用户}
     */
    @NotBlank(message = "{param.not_blank}")
    @Schema(description = "角色名称（中文可读名称）", example = "内容管理员", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    /**
     * 角色编码
     *
     * <p>系统内部唯一标识，用于代码中的权限判断。
     * 角色编码在整个系统内必须唯一，不能重复。
     *
     * <p>命名规范：
     * <ul>
     *   <li>必须以 {@code ROLE_} 开头</li>
     *   <li>后续部分使用大写字母、数字和下划线</li>
     *   <li>语义清晰，见名知意</li>
     * </ul>
     *
     * <p>验证规则：
     * <ul>
     *   <li>必填</li>
     *   <li>格式：{@code ^ROLE_[A-Z0-9_]+$}</li>
     *   <li>系统内唯一</li>
     * </ul>
     *
     * <p>示例：{@code ROLE_ADMIN}、{@code ROLE_CONTENT_ADMIN}、{@code ROLE_USER}
     */
    @NotBlank(message = "{param.not_blank}")
    @Pattern(regexp = "^ROLE_[A-Z0-9_]+$", message = "角色编码必须以 ROLE_ 开头，后续仅包含大写字母、数字和下划线")
    @Schema(
            description = "角色编码（系统内部唯一标识，如 ROLE_ADMIN）",
            example = "ROLE_CONTENT_ADMIN",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    /**
     * 角色描述
     *
     * <p>对角色职责和权限范围的补充说明，帮助管理员理解该角色的用途。
     *
     * <p>建议包含：
     * <ul>
     *   <li>角色的主要职责</li>
     *   <li>拥有的核心权限</li>
     *   <li>适用的使用场景</li>
     * </ul>
     *
     * <p>示例：{@code 负责内容审核、管理和发布，拥有内容模块的所有权限}
     */
    @Schema(description = "角色描述（可选，说明角色职责和权限范围）", example = "负责内容审核和管理")
    private String description;

    /**
     * 角色状态
     *
     * <p>控制角色的启用/禁用状态。禁用角色后，拥有该角色的用户将无法使用相关权限，
     * 但不会删除用户与角色的关联关系。
     *
     * <p>状态值：
     * <ul>
     *   <li>{@code 0}：禁用，角色暂时失效，用户无法使用该角色的权限</li>
     *   <li>{@code 1}：正常，角色正常启用，用户可以使用该角色的权限</li>
     * </ul>
     *
     * <p>默认值：{@code 1}（正常）
     *
     * <p>注意事项：
     * <ul>
     *   <li>禁用系统内置角色（如 ROLE_ADMIN）可能导致管理功能无法使用</li>
     *   <li>禁用角色后，建议通知相关用户</li>
     * </ul>
     */
    @Schema(
            description = "角色状态（0-禁用，1-正常）",
            example = "1",
            allowableValues = {"0", "1"})
    private Integer status;
}
