package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.UserCompliancePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户合规记录 MyBatis Mapper 接口。
 *
 * <p>负责 {@code sys_user_compliance} 表的持久化操作，记录用户的合规状态和违规历史。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface UserComplianceMapper extends BaseMapper<UserCompliancePO> {}
