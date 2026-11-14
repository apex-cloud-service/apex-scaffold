package com.apex.scaffold.integration.device.infrastructure.persistence.mapper;

import com.apex.scaffold.integration.device.infrastructure.persistence.po.DeviceTelemetryPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备遥测 Mapper（表 {@code iot_telemetry}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Mapper
public interface DeviceTelemetryMapper extends BaseMapper<DeviceTelemetryPO> {}
