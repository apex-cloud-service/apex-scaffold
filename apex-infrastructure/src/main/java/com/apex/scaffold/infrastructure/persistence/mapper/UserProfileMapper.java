package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.UserProfilePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户画像 MyBatis Mapper 接口。
 *
 * <p>负责 {@code biz_user_profile} 表的持久化操作，存储用户的兴趣爱好、交友偏好等画像数据。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfilePO> {}
