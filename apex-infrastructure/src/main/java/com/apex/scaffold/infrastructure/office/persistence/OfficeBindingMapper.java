package com.apex.scaffold.infrastructure.office.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 办公平台身份绑定 Mapper。
 *
 * <p>继承 MyBatis-Plus {@code BaseMapper}，提供 {@code office_identity_binding} 表
 * （外部平台用户身份与本地用户的绑定关系）的基础 CRUD 与条件查询能力，
 * 由 {@code OfficeRepositoryImpl} 配合 {@code OfficePersistenceConverter} 完成领域仓储实现。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Mapper
public interface OfficeBindingMapper extends BaseMapper<OfficeBindingPO> {}
