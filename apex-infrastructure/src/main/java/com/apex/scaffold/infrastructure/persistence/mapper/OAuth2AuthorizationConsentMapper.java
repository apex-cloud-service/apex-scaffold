package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.OAuth2AuthorizationConsentPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * OAuth2授权同意Mapper接口
 *
 * <p>提供对 {@code oauth2_authorization_consent} 表的基础CRUD操作。
 * 继承MyBatis-Plus的 {@link BaseMapper}，自动提供常用的数据库操作方法。
 *
 * <h2>自动提供的方法</h2>
 * <ul>
 *   <li>insert - 插入记录</li>
 *   <li>updateById - 根据ID更新</li>
 *   <li>deleteById - 根据ID删除</li>
 *   <li>selectById - 根据ID查询</li>
 *   <li>selectOne - 条件查询单条</li>
 *   <li>selectList - 条件查询列表</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Mapper
/**n * OAuth2AuthorizationConsentMappern *n * <p>TODO: 补充 Mapper 的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public interface OAuth2AuthorizationConsentMapper extends BaseMapper<OAuth2AuthorizationConsentPO> {}
