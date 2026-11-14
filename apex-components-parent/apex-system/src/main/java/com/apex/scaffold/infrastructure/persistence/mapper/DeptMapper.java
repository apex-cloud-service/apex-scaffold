package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.DeptPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 部门 Mapper 接口
 *
 * <p>负责 {@code sys_dept} 表的持久化操作，管理组织架构中的部门信息。
 * 继承 MyBatis-Plus {@link BaseMapper}，自动获得通用的单表 CRUD 操作。
 *
 * <h2>设计说明</h2>
 * <p>表名：{@code sys_dept}<br>
 * 实体：{@link DeptPO}<br>
 * 主键：{@code id}（Long 类型，雪花算法）
 *
 * <h2>设计说明</h2>
 * <p>部门采用树形结构设计：
 * <ul>
 *   <li>{@code parent_id}：父部门 ID，0 表示根部门</li>
 *   <li>{@code ancestors}：祖先链（逗号分隔），如 {@code "0,100,101"}</li>
 *   <li>{@code order_num}：同级部门的显示顺序</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <p>继承自 {@link BaseMapper} 的常用方法：
 * <ul>
 *   <li>{@code insert(entity)} - 插入一条部门记录</li>
 *   <li>{@code deleteById(id)} - 根据 ID 删除部门</li>
 *   <li>{@code updateById(entity)} - 根据 ID 更新部门</li>
 *   <li>{@code selectById(id)} - 根据 ID 查询部门</li>
 *   <li>{@code selectList(queryWrapper)} - 根据条件查询部门列表</li>
 *   <li>{@code selectPage(page, queryWrapper)} - 分页查询部门</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 查询所有根部门
 * List<DeptPO> rootDepts = deptMapper.selectList(
 *     new LambdaQueryWrapper<DeptPO>()
 *         .eq(DeptPO::getParentId, 0)
 *         .eq(DeptPO::getStatus, 1)
 *         .orderByAsc(DeptPO::getOrderNum)
 * );
 *
 * // 查询某部门的直接子部门
 * List<DeptPO> children = deptMapper.selectList(
 *     new LambdaQueryWrapper<DeptPO>()
 *         .eq(DeptPO::getParentId, 100L)
 *         .eq(DeptPO::getStatus, 1)
 * );
 *
 * // 查询某部门的所有子部门（包括多级）
 * List<DeptPO> allChildren = deptMapper.selectList(
 *     new LambdaQueryWrapper<DeptPO>()
 *         .apply("FIND_IN_SET({0}, ancestors)", 100L)
 *         .eq(DeptPO::getStatus, 1)
 * );
 *
 * // 插入新部门
 * DeptPO dept = DeptPO.builder()
 *     .parentId(100L)
 *     .ancestors("0,100")
 *     .name("研发部")
 *     .orderNum(1)
 *     .status(1)
 *     .build();
 * deptMapper.insert(dept);
 * }</pre>
 *
 * <h2>职责范围</h2>
 * <p>树形结构的组装通常在 Service 层完成：
 * <ol>
 *   <li>查询所有部门：{@code selectList(null)}</li>
 *   <li>在内存中按 {@code parent_id} 组装成树</li>
 *   <li>利用 {@code ancestors} 字段高效过滤子树</li>
 * </ol>
 *
 * <h2>职责范围</h2>
 * <pre>{@code
 * // 本部门数据
 * wrapper.eq(DeptPO::getId, currentDeptId);
 *
 * // 本部门及以下数据
 * wrapper.apply("FIND_IN_SET({0}, ancestors)", currentDeptId);
 *
 * // 自定义部门数据
 * wrapper.in(DeptPO::getId, allowedDeptIds);
 * }</pre>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>删除部门前需检查是否有子部门和关联用户</li>
 *   <li>更新 {@code parent_id} 时需同步更新 {@code ancestors} 字段</li>
 *   <li>多租户场景需添加 {@code tenant_id} 过滤条件</li>
 *   <li>建议在 {@code parent_id} 和 {@code tenant_id} 上建立索引</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 2.2.0
 * @see DeptPO
 * @see BaseMapper
 */
@Mapper
public interface DeptMapper extends BaseMapper<DeptPO> {}
