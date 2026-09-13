package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.OAuth2RegisteredClientPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * OAuth2注册客户端Mapper
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Mapper
/**n * OAuth2RegisteredClientMappern *n * <p>TODO: 补充 Mapper 的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public interface OAuth2RegisteredClientMapper extends BaseMapper<OAuth2RegisteredClientPO> {}
