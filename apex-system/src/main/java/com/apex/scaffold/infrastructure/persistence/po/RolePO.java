package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 角色持久化对象
 *
 * <p>对应数据库表 {@code sys_role}，存储系统角色信息。
 * 角色是 RBAC（基于角色的访问控制）权限模型的核心实体，用于将权限分组并分配给用户。
 *
 * <h2>设计说明</h2>
 * <p>表名：{@code sys_role}<br>
 * 主键：{@code id}（继承自 {@link BasePO}）<br>
 * 唯一索引：{@code uk_code}（角色编码唯一）
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>权限管理：定义系统中的各种角色及其权限范围</li>
 *   <li>用户授权：将角色分配给用户，实现权限控制</li>
 *   <li>数据权限：通过 dataScope 控制角色可访问的数据范围</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>格式：{@code ROLE_} 前缀 + 大写字母/数字/下划线</li>
 *   <li>示例：{@code ROLE_ADMIN}、{@code ROLE_USER}、{@code ROLE_OPERATOR}</li>
 *   <li>唯一性：系统内角色编码必须唯一</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code 1}：全部数据权限</li>
 *   <li>{@code 2}：本部门数据权限</li>
 *   <li>{@code 3}：本部门及以下数据权限</li>
 *   <li>{@code 4}：仅本人数据权限</li>
 *   <li>{@code 5}：自定义数据权限</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * RolePO role = RolePO.builder()
 *     .name("内容管理员")
 *     .code("ROLE_CONTENT_ADMIN")
 *     .description("负责内容审核和管理")
 *     .status(1)
 *     .dataScope(3)
 *     .build();
 * }</pre>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see BasePO
 * @see com.apex.scaffold.infrastructure.persistence.mapper.RoleMapper
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class RolePO extends BasePO {

    /**
     * 角色名称
     *
     * <p>面向管理员的可读名称，用于在管理界面展示。
     * 角色名称应简洁明了，能够清晰表达角色的职责。
     *
     * <p>示例：{@code 超级管理员}、{@code 内容管理员}、{@code 普通用户}
     */
    private String name;

    /**
     * 角色编码
     *
     * <p>系统内部唯一标识，用于代码中的权限判断。
     * 必须以 {@code ROLE_} 开头，后续使用大写字母、数字和下划线。
     *
     * <p>示例：{@code ROLE_ADMIN}、{@code ROLE_CONTENT_ADMIN}、{@code ROLE_USER}
     *
     * <p>唯一索引：{@code uk_code}
     */
    private String code;

    /**
     * 角色描述
     *
     * <p>对角色职责和权限范围的补充说明，帮助管理员理解该角色的用途。
     *
     * <p>示例：{@code 负责内容审核、管理和发布，拥有内容模块的所有权限}
     */
    private String description;

    /**
     * 角色状态
     *
     * <p>控制角色的启用/禁用状态。
     *
     * <p>状态值：
     * <ul>
     *   <li>{@code 0}：禁用，角色暂时失效，用户无法使用该角色的权限</li>
     *   <li>{@code 1}：正常，角色正常启用，用户可以使用该角色的权限</li>
     * </ul>
     *
     * <p>默认值：{@code 1}（正常）
     */
    private Integer status;

    /**
     * 数据权限类型编码
     *
     * <p>控制拥有该角色的用户可以访问的数据范围。
     *
     * <p>数据权限类型：
     * <ul>
     *   <li>{@code 1}：全部数据权限 - 可访问所有数据</li>
     *   <li>{@code 2}：本部门数据权限 - 仅可访问本部门数据</li>
     *   <li>{@code 3}：本部门及以下数据权限 - 可访问本部门及下级部门数据</li>
     *   <li>{@code 4}：仅本人数据权限 - 仅可访问自己创建的数据</li>
     *   <li>{@code 5}：自定义数据权限 - 通过配置指定可访问的部门</li>
     * </ul>
     *
     * <p>数据库默认值为 {@code NULL}；未配置的数据范围由授权解析器拒绝。
     */
    private Integer dataScope;

    /**
     * 更新角色的核心信息
     *
     * <p>用于更新角色的名称和描述，通常在角色编辑场景中使用。
     * 此方法不更新角色编码（code），因为编码作为唯一标识不应变更。
     *
     * @param name        角色名称，不能为空
     * @param description 角色描述，可为空
     */
    public void updateInfo(String name, String description) {
        setName(name);
        setDescription(description);
    }
}
