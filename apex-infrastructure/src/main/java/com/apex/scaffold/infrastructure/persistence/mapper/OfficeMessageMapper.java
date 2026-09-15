package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.OfficeMessagePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 办公平台消息 Mapper
 *
 * <p>提供消息数据的持久化操作，继承 MyBatis-Plus 的 BaseMapper。
 * 自动支持 CRUD 操作、分页查询、条件构造器等功能。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Mapper
public interface OfficeMessageMapper extends BaseMapper<OfficeMessagePO> {
}
