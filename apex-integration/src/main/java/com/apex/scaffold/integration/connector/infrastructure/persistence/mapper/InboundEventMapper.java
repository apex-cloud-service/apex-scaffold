package com.apex.scaffold.integration.connector.infrastructure.persistence.mapper;

import com.apex.scaffold.integration.connector.infrastructure.persistence.po.InboundEventPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 入站事件 Mapper（表 {@code int_inbound_event}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Mapper
public interface InboundEventMapper extends BaseMapper<InboundEventPO> {}
