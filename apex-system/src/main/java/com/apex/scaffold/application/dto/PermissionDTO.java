package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限数据传输对象
 *
 * <p>用于接口层返回权限信息，避免领域实体直接暴露到表示层。
 * 权限是 RBAC 权限模型的基本单元，通过角色与权限的关联实现灵活的访问控制。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>权限列表查询：管理员查看系统所有权限</li>
 *   <li>角色授权：为角色分配权限时展示可选权限列表</li>
 *   <li>权限树构建：构建菜单权限树供前端展示</li>
 * </ul>
 *
 * <h2>权限类型</h2>
 * <p>常见权限类型：
 * <ul>
 *   <li>菜单权限：控制菜单的显示和隐藏</li>
 *   <li>按钮权限：控制页面内按钮的显示</li>
 *   <li>接口权限：控制 API 的访问</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "权限DTO")
public class PermissionDTO {

    /**
     * 权限主键 ID
     *
     * <p>权限的唯一标识，通常使用雪花算法生成。
     */
    @Schema(description = "权限ID")
    private Long id;

    /**
     * 权限名称
     *
     * <p>面向管理员的可读名称，用于在管理界面展示。
     *
     * <p>示例：{@code 用户管理}、{@code 删除用户}、{@code 查看报表}
     */
    @Schema(description = "权限名称")
    private String name;

    /**
     * 权限编码
     *
     * <p>系统内部唯一标识，用于代码中的权限判断。
     * 权限编码应遵循统一的命名规范，便于理解和维护。
     *
     * <p>命名规范：{@code 模块:操作}
     * <ul>
     *   <li>{@code user:view}：查看用户</li>
     *   <li>{@code user:create}：创建用户</li>
     *   <li>{@code user:delete}：删除用户</li>
     *   <li>{@code photo:upload}：上传照片</li>
     * </ul>
     */
    @Schema(description = "权限编码（格式: 模块:操作，如 user:view）")
    private String code;

    /**
     * 权限描述
     *
     * <p>对权限用途和影响范围的补充说明。
     *
     * <p>示例：{@code 允许查看所有用户的基本信息和状态}
     */
    @Schema(description = "权限描述")
    private String description;

    /**
     * 权限类型
     *
     * <p>用于分类管理和前端展示逻辑。
     *
     * <p>常用类型：
     * <ul>
     *   <li>{@code 1}：菜单权限</li>
     *   <li>{@code 2}：按钮权限</li>
     *   <li>{@code 3}：接口权限</li>
     * </ul>
     */
    @Schema(description = "权限类型（1-菜单 2-按钮 3-接口）")
    private Integer type;

    /**
     * 权限状态
     *
     * <p>控制权限的启用/禁用状态。
     *
     * <p>状态值：
     * <ul>
     *   <li>{@code 0}：禁用，拥有此权限的角色无法使用该权限</li>
     *   <li>{@code 1}：正常，拥有此权限的角色可以使用该权限</li>
     * </ul>
     */
    @Schema(description = "权限状态（0-禁用 1-正常）")
    private Integer status;

    /**
     * 创建时间
     *
     * <p>权限记录首次创建的时间戳。
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
