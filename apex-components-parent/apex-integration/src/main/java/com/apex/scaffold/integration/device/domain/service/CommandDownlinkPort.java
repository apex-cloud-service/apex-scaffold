package com.apex.scaffold.integration.device.domain.service;

import com.apex.scaffold.integration.device.domain.DeviceCommand;
import reactor.core.publisher.Mono;

/**
 * 命令下行发布端口（方案 M5 / 批次 E3）。
 *
 * <p>领域端口：命令下发后经本端口投递到设备（默认实现为 MQTT 桥接发布到
 * {@code apex/{tenantCode}/{deviceKey}/down/{commandId}}）。端口缺失（MQTT 裁剪或
 * 未启用）时命令保持 PENDING，由超时扫描兜底置 TIMEOUT。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface CommandDownlinkPort {

    /**
     * 向设备发布一条下行命令。
     *
     * @param command 命令（须携带租户、设备标识与业务命令 ID）
     * @return 是否发布成功
     */
    Mono<Boolean> publish(DeviceCommand command);
}
