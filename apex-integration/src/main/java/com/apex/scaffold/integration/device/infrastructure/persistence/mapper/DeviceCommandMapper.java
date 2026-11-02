package com.apex.scaffold.integration.device.infrastructure.persistence.mapper;

import com.apex.scaffold.integration.device.infrastructure.persistence.po.DeviceCommandPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备下行命令 Mapper（表 {@code iot_command}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Mapper
public interface DeviceCommandMapper extends BaseMapper<DeviceCommandPO> {}
