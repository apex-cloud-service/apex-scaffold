package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.SystemMessagePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统消息 MyBatis Mapper 接口。
 *
 * <p>负责 {@code sys_system_message} 表的持久化操作，管理平台推送的系统通知消息。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface SystemMessageMapper extends BaseMapper<SystemMessagePO> {}
