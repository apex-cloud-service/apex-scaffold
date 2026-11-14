package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.TenantPackagePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户套餐 Mapper（sys_tenant_package，平台级定义表，方案批次 B3）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Mapper
public interface TenantPackageMapper extends BaseMapper<TenantPackagePO> {}
