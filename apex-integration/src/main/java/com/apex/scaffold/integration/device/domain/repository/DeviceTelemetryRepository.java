package com.apex.scaffold.integration.device.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.integration.device.domain.DeviceTelemetry;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 设备遥测仓储端口（方案 M5 / 批次 E3）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface DeviceTelemetryRepository {

    /**
     * 批量落库（单次 jdbc 访问内循环插入，jdbc 池隔离）。
     *
     * @param records 遥测记录（含租户）
     * @return 完成信号
     */
    Mono<Void> insertAll(List<DeviceTelemetry> records);

    /**
     * 分页查询设备遥测（租户限定，按上报时间倒序）。
     *
     * @param tenantId 租户编码
     * @param deviceId 设备主键（用于反查 deviceKey 的归属校验前置完成）
     * @param deviceKey 设备标识
     * @param pageNum  页码（1 起）
     * @param pageSize 页大小
     * @return 分页结果
     */
    Mono<PageResponse<DeviceTelemetry>> page(
            String tenantId, Long deviceId, String deviceKey, int pageNum, int pageSize);
}
