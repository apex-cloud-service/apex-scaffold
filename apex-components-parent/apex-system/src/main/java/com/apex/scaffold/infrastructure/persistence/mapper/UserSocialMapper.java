package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.UserSocialPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户社交身份绑定 MyBatis Mapper 接口。
 *
 * <p>负责 {@code sys_user_social} 表的持久化操作，管理多端微信身份绑定记录。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface UserSocialMapper extends BaseMapper<UserSocialPO> {}
