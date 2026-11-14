package com.apex.scaffold.integration.device.infrastructure.persistence.mapper;

import com.apex.scaffold.integration.device.infrastructure.persistence.po.DeviceShadowPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备影子 Mapper（表 {@code iot_device_shadow}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Mapper
public interface DeviceShadowMapper extends BaseMapper<DeviceShadowPO> {}
