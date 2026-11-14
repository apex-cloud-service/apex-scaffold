package com.apex.scaffold.integration.device.infrastructure.persistence.mapper;

import com.apex.scaffold.integration.device.infrastructure.persistence.po.DeviceRulePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 轻量规则 Mapper（表 {@code iot_rule}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Mapper
public interface DeviceRuleMapper extends BaseMapper<DeviceRulePO> {}
