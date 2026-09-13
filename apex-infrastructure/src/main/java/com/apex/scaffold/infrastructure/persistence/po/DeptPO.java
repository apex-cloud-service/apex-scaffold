package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 部门持久化对象
 *
 * <p>对应数据库表 {@code sys_dept}，存储组织架构中的部门信息。
 * 采用树形结构设计，通过 {@code parent_id} 实现父子关联，
 * 通过 {@code ancestors} 存储祖先链，支持高效的数据权限过滤。
 *
 * <h2>表结构</h2>
 * <p>表名：{@code sys_dept}<br>
 * 主键：{@code id}（雪花算法生成）<br>
 * 索引：{@code idx_parent_id}、{@code idx_tenant_id}
 *
 * <h2>树形结构</h2>
 * <ul>
 *   <li>{@code parent_id}：父部门 ID，0 表示根部门</li>
 *   <li>{@code ancestors}：祖先链，逗号分隔（如 {@code 0,100,101}）</li>
 *   <li>{@code order_num}：同级部门的显示顺序</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>组织架构管理：构建和展示企业组织架构树</li>
 *   <li>数据权限控制：基于部门层级过滤数据（本部门、本部门及以下等）</li>
 *   <li>用户归属：用户通过 {@code dept_id} 关联到部门</li>
 * </ul>
 *
 * <h2>数据权限支持</h2>
 * <ul>
 *   <li>本部门数据：{@code dept_id = ?}</li>
 *   <li>本部门及以下：{@code FIND_IN_SET(?, ancestors)}</li>
 *   <li>自定义部门：{@code dept_id IN (?)}</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * DeptPO dept = DeptPO.builder()
 *     .parentId(100L)
 *     .ancestors("0,100")
 *     .name("研发部")
 *     .orderNum(1)
 *     .leader("张三")
 *     .phone("13800138000")
 *     .email("dev@company.com")
 *     .status(1)
 *     .tenantId("tenant001")
 *     .build();
 * }</pre>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 2.2.0
 * @see com.apex.scaffold.infrastructure.persistence.mapper.DeptMapper
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_dept")
/**n * DeptPO - 持久化对象n *n * <p>对应数据库表：TODO: 补充表名n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class DeptPO {

    /**
     * 租户 ID
     *
     * <p>多租户场景下的租户标识，用于数据隔离。
     * 同一租户下的部门不能跨租户访问。
     *
     * <p>示例：{@code tenant001}、{@code tenant002}
     */
    @TableField("tenant_id")
    private String tenantId;

    /**
     * 主键 ID
     *
     * <p>部门的唯一标识，使用雪花算法生成。
     * 雪花算法保证分布式环境下的 ID 唯一性和趋势递增。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 父部门 ID
     *
     * <p>指向父级部门的 ID，构成树形结构。
     *
     * <p>特殊值：
     * <ul>
     *   <li>{@code 0}：根部门（顶级部门）</li>
     *   <li>{@code > 0}：指向具体的父部门</li>
     * </ul>
     *
     * <p>示例：
     * <ul>
     *   <li>公司总部：{@code parent_id = 0}</li>
     *   <li>研发部：{@code parent_id = 100}（公司总部的 ID）</li>
     * </ul>
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 祖先链
     *
     * <p>从根节点到当前节点的所有父节点 ID，逗号分隔。
     * 用于高效查询某部门的所有子部门（包括多级子部门）。
     *
     * <p>格式：{@code parent_id1,parent_id2,...,parent_idN}
     *
     * <p>示例：
     * <ul>
     *   <li>公司总部：{@code "0"}（根节点）</li>
     *   <li>研发部：{@code "0,100"}（公司总部 → 研发部）</li>
     *   <li>后端组：{@code "0,100,101"}（公司总部 → 研发部 → 后端组）</li>
     * </ul>
     *
     * <p>查询用途：
     * <ul>
     *   <li>查询本部门及以下：{@code FIND_IN_SET(当前部门ID, ancestors)}</li>
     * </ul>
     */
    private String ancestors;

    /**
     * 部门名称
     *
     * <p>部门的显示名称，用于在组织架构树和界面上展示。
     *
     * <p>示例：{@code 研发部}、{@code 后端组}、{@code 产品部}
     */
    private String name;

    /**
     * 显示顺序
     *
     * <p>同级部门的排序序号，数字越小越靠前。
     * 用于在组织架构树中控制兄弟节点的显示顺序。
     *
     * <p>示例：{@code 1}、{@code 2}、{@code 3}
     */
    @TableField("order_num")
    private Integer orderNum;

    /**
     * 部门负责人
     *
     * <p>部门负责人的姓名（可选）。
     *
     * <p>示例：{@code 张三}、{@code 李四}
     */
    private String leader;

    /**
     * 联系电话
     *
     * <p>部门的联系电话（可选）。
     *
     * <p>示例：{@code 13800138000}
     */
    private String phone;

    /**
     * 邮箱
     *
     * <p>部门的联系邮箱（可选）。
     *
     * <p>示例：{@code dev@company.com}
     */
    private String email;

    /**
     * 部门状态
     *
     * <p>控制部门的启用/停用状态。
     *
     * <p>状态值：
     * <ul>
     *   <li>{@code 0}：停用，该部门暂时失效，不能分配用户</li>
     *   <li>{@code 1}：正常，部门正常启用</li>
     * </ul>
     *
     * <p>默认值：{@code 1}（正常）
     */
    private Integer status;

    /**
     * 创建时间
     *
     * <p>记录创建的时间戳，由 MyBatis-Plus 自动填充。
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     *
     * <p>记录最后更新的时间戳，由 MyBatis-Plus 自动填充。
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 版本号（乐观锁）
     *
     * <p>用于实现乐观锁，防止并发更新冲突。
     * 每次更新时版本号自动 +1。
     *
     * <p>初始值：{@code 0}
     */
    @Version
    @TableField(value = "version", fill = FieldFill.INSERT)
    private Integer version;

    /**
     * 逻辑删除标记
     *
     * <p>逻辑删除标记，删除记录时不会真正从数据库删除，而是标记为已删除。
     *
     * <p>删除状态：
     * <ul>
     *   <li>{@code 0}：未删除</li>
     *   <li>{@code 1}：已删除</li>
     * </ul>
     *
     * <p>默认值：{@code 0}（未删除）
     */
    @TableLogic
    private Integer deleted;
}
