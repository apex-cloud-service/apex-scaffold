package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.PolicyVersionPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 政策版本记录 MyBatis Mapper 接口。
 *
 * <p>负责 {@code sys_policy_version} 表的持久化操作，管理用户协议和隐私政策的版本记录。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface PolicyVersionMapper extends BaseMapper<PolicyVersionPO> {}
