package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.TenantPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户 MyBatis Mapper 接口。
 *
 * <p>负责 {@code sys_tenant} 表的持久化操作。继承 MyBatis-Plus {@link BaseMapper}
 * 获得通用单表 CRUD 操作。租户表已在
 * {@code DefaultTenantLineHandler.IGNORE_TABLES} 中排除，不受租户拦截器影响。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Mapper
/**n * TenantMappern *n * <p>TODO: 补充 Mapper 的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public interface TenantMapper extends BaseMapper<TenantPO> {}
