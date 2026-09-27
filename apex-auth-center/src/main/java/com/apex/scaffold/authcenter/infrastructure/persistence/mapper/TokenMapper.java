package com.apex.scaffold.authcenter.infrastructure.persistence.mapper;

import com.apex.scaffold.authcenter.infrastructure.persistence.po.TokenPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 认证数据访问接口。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Mapper
public interface TokenMapper extends BaseMapper<TokenPO> {}
