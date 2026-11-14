package com.apex.scaffold.office.off.infrastructure.persistence.mapper;

import com.apex.scaffold.office.off.infrastructure.persistence.po.OffMessageDeliveryPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息渠道投递记录 MyBatis Mapper 接口。
 *
 * <p>负责 {@code off_message_delivery} 表的持久化操作，继承 MyBatis-Plus
 * {@link BaseMapper} 获得通用单表 CRUD 能力。</p>
 *
 * @author Apex Team
 * @since 1.3.2
 * @see OffMessageDeliveryPO
 */
@Mapper
public interface OffMessageDeliveryMapper extends BaseMapper<OffMessageDeliveryPO> {}
