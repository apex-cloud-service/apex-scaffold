package com.apex.scaffold.integration.device.infrastructure.persistence.mapper;

import com.apex.scaffold.integration.device.infrastructure.persistence.po.DevicePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 物联网设备 Mapper（表 {@code iot_device}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Mapper
public interface DeviceMapper extends BaseMapper<DevicePO> {}
