package com.apex.scaffold.integration.device.application;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.device.domain.Device;
import com.apex.scaffold.integration.device.domain.DeviceTelemetry;
import com.apex.scaffold.integration.device.domain.repository.DeviceRepository;
import com.apex.scaffold.integration.device.domain.repository.DeviceTelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 设备查询用例（方案 M5 / 批次 E3，管理接口与 D5 聚合统计消费）。
 *
 * <p>跨租户访问一律视为不存在（fail-closed）；遥测查询先经设备归属校验再按
 * device_key 查询，杜绝跨租户读取。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Service
@RequiredArgsConstructor
public class DeviceQueryService {

    private final DeviceRepository deviceRepository;

    private final DeviceTelemetryRepository telemetryRepository;

    /**
     * 分页查询租户设备。
     *
     * @param tenantId 租户编码
     * @param status   状态（可空）
     * @param pageNum  页码
     * @param pageSize 页大小
     * @return 分页结果
     */
    public Mono<PageResponse<Device>> page(String tenantId, String status, int pageNum, int pageSize) {
        return deviceRepository.page(tenantId, status, pageNum, pageSize);
    }

    /**
     * 设备详情（含 secretHash 属敏感凭据，响应映射方不得透出）。
     *
     * @param tenantId 租户编码
     * @param id       设备主键
     * @return 设备
     */
    public Mono<Device> get(String tenantId, Long id) {
        return deviceRepository
                .findById(tenantId, id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "设备不存在")));
    }

    /**
     * 租户内设备总数（D5 聚合门面统计口径：deviceCount）。
     *
     * @param tenantId 租户编码
     * @return 设备总数
     */
    public Mono<Long> deviceCount(String tenantId) {
        return deviceRepository.count(tenantId);
    }

    /**
     * 查询设备遥测（先校验设备归属，再按 device_key 分页）。
     *
     * @param tenantId 租户编码
     * @param deviceId 设备主键
     * @param pageNum  页码
     * @param pageSize 页大小
     * @return 遥测分页
     */
    public Mono<PageResponse<DeviceTelemetry>> telemetry(String tenantId, Long deviceId, int pageNum, int pageSize) {
        return get(tenantId, deviceId)
                .flatMap(device ->
                        telemetryRepository.page(tenantId, device.getId(), device.getDeviceKey(), pageNum, pageSize));
    }
}
