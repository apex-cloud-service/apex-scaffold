package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.RolePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 MyBatis Mapper 接口。
 *
 * <p>负责 {@code sys_role} 表的持久化操作，管理系统中的角色定义和权限集合。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface RoleMapper extends BaseMapper<RolePO> {}
