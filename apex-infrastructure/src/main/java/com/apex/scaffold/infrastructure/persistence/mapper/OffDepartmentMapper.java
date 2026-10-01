package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.OffDepartmentPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 办公部门 MyBatis Mapper 接口
 *
 * <p>负责 {@code off_department} 表的持久化操作，管理从第三方办公平台同步的部门组织架构。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。
 *
 * <h2>表映射</h2>
 * <p>表名：{@code off_department}<br>
 * 实体：{@link OffDepartmentPO}<br>
 * 主键：{@code id}（Long 类型，雪花算法）
 *
 * <h2>部门字段</h2>
 * <ul>
 *   <li>{@code department_name}：部门名称</li>
 *   <li>{@code parent_id}：父部门 ID（0 表示根部门）</li>
 *   <li>{@code external_dept_id}：外部平台部门 ID</li>
 *   <li>{@code platform}：来源平台（FEISHU、DINGTALK、WECOM）</li>
 *   <li>{@code leader_user_id}：部门负责人用户 ID</li>
 *   <li>{@code order_num}：显示顺序</li>
 *   <li>{@code status}：部门状态（启用、停用）</li>
 * </ul>
 *
 * <h2>树形结构</h2>
 * <p>部门采用树形结构设计：
 * <ul>
 *   <li>{@code parent_id}：父部门 ID，构建树形层级关系</li>
 *   <li>{@code order_num}：同级部门的显示顺序</li>
 *   <li>支持多级部门嵌套，无深度限制</li>
 * </ul>
 *
 * <h2>同步来源</h2>
 * <ul>
 *   <li>飞书（Lark）：从飞书组织架构同步部门信息</li>
 *   <li>钉钉（DingTalk）：从钉钉组织架构同步部门</li>
 *   <li>企业微信（WeChat Work）：从企业微信通讯录同步</li>
 * </ul>
 *
 * <h2>业务场景</h2>
 * <ul>
 *   <li>组织架构同步：从第三方平台同步部门结构</li>
 *   <li>部门查询：按名称、层级查询部门</li>
 *   <li>外部映射：通过 {@code external_dept_id} 关联外部平台部门</li>
 *   <li>树形展示：构建前端组织架构树</li>
 * </ul>
 *
 * <h2>通用方法</h2>
 * <p>继承自 {@link BaseMapper} 的常用方法：
 * <ul>
 *   <li>{@code insert(entity)} - 插入部门记录</li>
 *   <li>{@code updateById(entity)} - 更新部门信息</li>
 *   <li>{@code selectById(id)} - 根据 ID 查询部门</li>
 *   <li>{@code selectList(wrapper)} - 条件查询部门列表</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see OffDepartmentPO
 * @see BaseMapper
 */
@Mapper
public interface OffDepartmentMapper extends BaseMapper<OffDepartmentPO> {}
