package com.apex.scaffold.integration.connector.infrastructure.persistence.mapper;

import com.apex.scaffold.integration.connector.infrastructure.persistence.po.OutboundCallLogPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 出站调用审计 Mapper（表 {@code int_outbound_call_log}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Mapper
public interface OutboundCallLogMapper extends BaseMapper<OutboundCallLogPO> {}
