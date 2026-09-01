package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.RoleDeptPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-部门关联 Mapper。
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper} 提供 {@code sys_role_dept} 表的
 * 基础 CRUD 能力，用于维护角色与部门的数据权限归属关系。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see RoleDeptPO
 */
@Mapper
public interface RoleDeptMapper extends BaseMapper<RoleDeptPO> {}
