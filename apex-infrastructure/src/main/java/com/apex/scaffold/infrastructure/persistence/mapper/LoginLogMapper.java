package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.LoginLogPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 MyBatis Mapper 接口。
 *
 * <p>负责 {@code sys_login_log} 表的持久化操作，管理登录尝试记录。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
/**n * LoginLogMappern *n * <p>TODO: 补充 Mapper 的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public interface LoginLogMapper extends BaseMapper<LoginLogPO> {}
