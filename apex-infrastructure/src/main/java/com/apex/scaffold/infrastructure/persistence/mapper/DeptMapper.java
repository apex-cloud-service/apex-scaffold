package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.DeptPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 部门 Mapper。
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper} 提供 sys_dept 基础 CRUD，
 * 树形查询由 {@code DeptService} 按 parentId 组装。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@Mapper
public interface DeptMapper extends BaseMapper<DeptPO> {}
