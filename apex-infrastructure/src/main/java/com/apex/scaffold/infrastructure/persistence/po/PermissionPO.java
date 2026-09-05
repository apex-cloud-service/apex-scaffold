package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 权限持久化对象
 *
 * <p>对应数据库表 {@code sys_permission}，存储系统权限信息。
 * 权限是 RBAC（基于角色的访问控制）权限模型的基本单元，定义了用户可以执行的具体操作。
 *
 * <h2>表结构</h2>
 * <p>表名：{@code sys_permission}<br>
 * 主键：{@code id}（继承自 {@link BasePO}）<br>
 * 唯一索引：{@code uk_code}（权限编码唯一）
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>权限定义：定义系统中的各种操作权限</li>
 *   <li>角色授权：将权限分配给角色</li>
 *   <li>权限验证：在接口或方法上验证用户是否拥有指定权限</li>
 * </ul>
 *
 * <h2>权限编码规范</h2>
 * <ul>
 *   <li>格式：{@code 模块:资源:操作}</li>
 *   <li>示例：{@code user:list:view}、{@code content:article:edit}、{@code system:role:delete}</li>
 *   <li>唯一性：系统内权限编码必须唯一</li>
 * </ul>
 *
 * <h2>权限类型</h2>
 * <ul>
 *   <li>{@code 1}：菜单权限 - 控制菜单是否显示</li>
 *   <li>{@code 2}：按钮权限 - 控制按钮是否可见/可用</li>
 *   <li>{@code 3}：接口权限 - 控制 API 接口是否可访问</li>
 *   <li>{@code 4}：数据权限 - 控制数据的访问范围</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * PermissionPO permission = PermissionPO.builder()
 *     .name("查看用户列表")
 *     .code("user:list:view")
 *     .description("允许查看用户列表页面")
 *     .type(1)
 *     .status(1)
 *     .build();
 * }</pre>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see BasePO
 * @see com.apex.scaffold.infrastructure.persistence.mapper.PermissionMapper
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class PermissionPO extends BasePO {

    /**
     * 权限名称
     *
     * <p>面向管理员的可读名称，用于在管理界面展示。
     * 权限名称应简洁明了，能够清晰表达权限的作用。
     *
     * <p>示例：{@code 查看用户列表}、{@code 编辑文章}、{@code 删除角色}
     */
    private String name;

    /**
     * 权限编码
     *
     * <p>系统内部唯一标识，用于代码中的权限判断。
     * 建议使用 {@code 模块:资源:操作} 的格式，便于理解和维护。
     *
     * <p>示例：
     * <ul>
     *   <li>{@code user:list:view} - 查看用户列表</li>
     *   <li>{@code content:article:edit} - 编辑文章</li>
     *   <li>{@code system:role:delete} - 删除角色</li>
     * </ul>
     *
     * <p>唯一索引：{@code uk_code}
     */
    private String code;

    /**
     * 权限描述
     *
     * <p>对权限功能和使用场景的补充说明，帮助管理员理解该权限的用途。
     *
     * <p>示例：{@code 允许查看用户列表页面，包括用户的基本信息和状态}
     */
    private String description;

    /**
     * 权限类型
     *
     * <p>定义权限的类别，不同类型的权限在系统中的应用场景不同。
     *
     * <p>权限类型：
     * <ul>
     *   <li>{@code 1}：菜单权限 - 控制菜单是否在导航栏显示</li>
     *   <li>{@code 2}：按钮权限 - 控制页面上的按钮是否可见或可用</li>
     *   <li>{@code 3}：接口权限 - 控制后端 API 接口是否可访问</li>
     *   <li>{@code 4}：数据权限 - 控制用户可以访问的数据范围</li>
     * </ul>
     *
     * <p>默认值：{@code 3}（接口权限）
     */
    private Integer type;

    /**
     * 权限状态
     *
     * <p>控制权限的启用/禁用状态。
     *
     * <p>状态值：
     * <ul>
     *   <li>{@code 0}：禁用，权限暂时失效，拥有该权限的用户无法使用相关功能</li>
     *   <li>{@code 1}：正常，权限正常启用，拥有该权限的用户可以正常使用相关功能</li>
     * </ul>
     *
     * <p>默认值：{@code 1}（正常）
     */
    private Integer status;

    /**
     * 更新权限的核心信息
     *
     * <p>用于更新权限的名称、描述和状态，通常在权限编辑场景中使用。
     * 此方法不更新权限编码（code）和类型（type），因为它们作为关键属性不应变更。
     *
     * @param name        权限名称，不能为空
     * @param description 权限描述，可为空
     * @param status      状态（0-禁用，1-正常），不能为空
     */
    public void updateInfo(String name, String description, Integer status) {
        setName(name);
        setDescription(description);
        setStatus(status);
    }
}
