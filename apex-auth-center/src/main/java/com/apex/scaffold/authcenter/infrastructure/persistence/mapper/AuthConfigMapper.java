package com.apex.scaffold.authcenter.infrastructure.persistence.mapper;

import com.apex.scaffold.authcenter.infrastructure.persistence.po.AuthConfigPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 认证配置表的数据访问映射。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Mapper
public interface AuthConfigMapper extends BaseMapper<AuthConfigPO> {}
