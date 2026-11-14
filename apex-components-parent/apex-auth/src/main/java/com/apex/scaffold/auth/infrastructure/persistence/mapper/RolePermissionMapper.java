package com.apex.scaffold.auth.infrastructure.persistence.mapper;

import com.apex.scaffold.auth.infrastructure.persistence.po.RolePermissionPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 认证数据访问接口。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionPO> {}
