package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 办公部门持久化对象
 *
 * <p>对应数据库表 {@code off_department}，存储从第三方办公平台（飞书、钉钉、企业微信）同步的部门组织架构信息。
 * 继承 {@link BasePO} 获得通用字段（ID、创建时间、更新时间、逻辑删除等）。
 *
 * <h2>核心字段</h2>
 * <ul>
 *   <li>{@link #deptCode} - 部门编码，企业内唯一标识</li>
 *   <li>{@link #deptName} - 部门名称</li>
 *   <li>{@link #parentId} - 父部门 ID（0 表示根部门）</li>
 *   <li>{@link #deptPath} - 部门路径（如 "/1/2/5"，用于快速查询上级部门）</li>
 *   <li>{@link #deptLevel} - 部门层级（1=一级部门，2=二级部门，以此类推）</li>
 *   <li>{@link #sortOrder} - 排序号（同级部门按此字段升序排列）</li>
 *   <li>{@link #leaderId} - 部门负责人 ID（关联 {@code off_employee}）</li>
 *   <li>{@link #leaderName} - 部门负责人姓名（冗余字段，便于查询）</li>
 *   <li>{@link #phone} - 部门联系电话</li>
 *   <li>{@link #email} - 部门联系邮箱</li>
 *   <li>{@link #status} - 部门状态（ACTIVE=正常、DISABLED=停用）</li>
 *   <li>{@link #syncSource} - 同步来源平台（FEISHU=飞书、DINGTALK=钉钉、WECOM=企业微信）</li>
 *   <li>{@link #externalId} - 外部平台部门 ID（第三方平台的唯一标识）</li>
 *   <li>{@link #tenantId} - 租户标识（多租户隔离）</li>
 * </ul>
 *
 * <h2>树形结构</h2>
 * <p>部门采用树形结构存储，通过 {@code parentId} 构建父子关系：
 * <ul>
 *   <li>根部门：{@code parentId = 0}</li>
 *   <li>子部门：{@code parentId} 指向父部门 ID</li>
 *   <li>路径字段：{@code deptPath} 记录从根到当前节点的完整路径</li>
 *   <li>层级字段：{@code deptLevel} 记录部门在树中的深度</li>
 * </ul>
 *
 * <h2>业务场景</h2>
 * <ul>
 *   <li>组织架构同步：从第三方平台定期同步部门结构</li>
 *   <li>部门树查询：构建和展示完整的组织架构树</li>
 *   <li>员工归属：通过 {@code deptId} 关联员工所属部门</li>
 *   <li>权限控制：基于部门的数据权限过滤</li>
 * </ul>
 *
 * <h2>数据来源</h2>
 * <ul>
 *   <li>飞书（Lark）：通过飞书 API 同步部门架构</li>
 *   <li>钉钉（DingTalk）：通过钉钉 API 同步部门信息</li>
 *   <li>企业微信（WeChat Work）：通过企业微信 API 同步部门结构</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>部门编码 {@code deptCode} 在租户内应保持唯一</li>
 *   <li>外部平台 ID {@code externalId} 用于数据同步的幂等性保证</li>
 *   <li>删除部门时需检查是否存在子部门和员工</li>
 *   <li>租户隔离通过 {@code tenantId} 字段实现</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see BasePO
 * @see OffEmployeePO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("off_department")
public class OffDepartmentPO extends BasePO {

    @TableField("dept_code")
    private String deptCode;

    @TableField("dept_name")
    private String deptName;

    @TableField("parent_id")
    private Long parentId;

    @TableField("dept_path")
    private String deptPath;

    @TableField("dept_level")
    private Integer deptLevel;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("leader_id")
    private Long leaderId;

    @TableField("leader_name")
    private String leaderName;

    @TableField("phone")
    private String phone;

    @TableField("email")
    private String email;

    @TableField("status")
    private String status;

    @TableField("sync_source")
    private String syncSource;

    @TableField("external_id")
    private String externalId;

    @TableField("tenant_id")
    private String tenantId;
}
