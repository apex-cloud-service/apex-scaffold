package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.RolePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 Mapper 接口
 *
 * <p>负责 {@code sys_role} 表的持久化操作，管理系统中的角色定义和权限集合。
 * 继承 MyBatis-Plus {@link BaseMapper}，自动获得通用的单表 CRUD 操作。
 *
 * <h2>设计说明</h2>
 * <p>表名：{@code sys_role}<br>
 * 实体：{@link RolePO}<br>
 * 主键：{@code id}（Long 类型）
 *
 * <h2>职责范围</h2>
 * <p>继承自 {@link BaseMapper} 的常用方法：
 * <ul>
 *   <li>{@code insert(entity)} - 插入一条记录</li>
 *   <li>{@code deleteById(id)} - 根据 ID 删除</li>
 *   <li>{@code updateById(entity)} - 根据 ID 更新</li>
 *   <li>{@code selectById(id)} - 根据 ID 查询</li>
 *   <li>{@code selectList(queryWrapper)} - 根据条件查询列表</li>
 *   <li>{@code selectPage(page, queryWrapper)} - 分页查询</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 插入角色
 * RolePO role = RolePO.builder()
 *     .name("内容管理员")
 *     .code("ROLE_CONTENT_ADMIN")
 *     .status(1)
 *     .build();
 * roleMapper.insert(role);
 *
 * // 根据 ID 查询
 * RolePO role = roleMapper.selectById(100L);
 *
 * // 条件查询
 * List<RolePO> roles = roleMapper.selectList(
 *     new LambdaQueryWrapper<RolePO>()
 *         .eq(RolePO::getStatus, 1)
 *         .like(RolePO::getName, "管理员")
 * );
 *
 * // 更新角色
 * role.setName("高级内容管理员");
 * roleMapper.updateById(role);
 * }</pre>
 *
 * <h2>设计说明</h2>
 * <p>如需复杂查询，可在 {@code resources/mapper/RoleMapper.xml} 中定义自定义 SQL：
 * <pre>{@code
 * <select id="findRolesWithPermissions" resultType="...">
 *     SELECT r.*, p.name AS permission_name
 *     FROM sys_role r
 *     LEFT JOIN sys_role_permission rp ON r.id = rp.role_id
 *     LEFT JOIN sys_permission p ON rp.permission_id = p.id
 *     WHERE r.id = #{roleId}
 * </select>
 * }</pre>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>所有写操作（insert/update/delete）应在事务中执行</li>
 *   <li>角色编码 {@code code} 字段有唯一索引，插入时注意避免重复</li>
 *   <li>删除角色前应先清理 {@code sys_role_permission} 和 {@code sys_user_role} 关联数据</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see RolePO
 * @see BaseMapper
 */
@Mapper
public interface RoleMapper extends BaseMapper<RolePO> {}
