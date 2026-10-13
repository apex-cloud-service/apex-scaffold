package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.CreditAccountPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 积分账户 MyBatis Mapper 接口。
 *
 * <p>负责 {@code biz_credit_account} 表的持久化操作，管理用户算力积分的账户余额。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface CreditAccountMapper extends BaseMapper<CreditAccountPO> {}
