package com.apex.scaffold.office.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 办公平台应用配置 Mapper。
 *
 * <p>继承 MyBatis-Plus {@code BaseMapper}，提供 {@code office_platform_app} 表
 * （租户维度的办公平台应用凭证与配置）的基础 CRUD 与条件查询能力，
 * 由 {@code OfficeRepositoryImpl} 配合 {@code OfficePersistenceConverter} 完成领域仓储实现。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Mapper
public interface OfficeAppMapper extends BaseMapper<OfficeAppPO> {}
