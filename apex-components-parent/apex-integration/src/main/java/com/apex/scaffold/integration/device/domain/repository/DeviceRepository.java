package com.apex.scaffold.integration.device.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.integration.device.domain.Device;
import reactor.core.publisher.Mono;

/**
 * 设备仓储端口（方案 M5 / 批次 E3）。
 *
 * <p>全部查询以租户为作用域（显式 {@code tenant_id} 条件，叠加行级拦截器）；
 * 写操作依赖逻辑删除（{@code deleted}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface DeviceRepository {

    /**
     * 按设备标识查询（租户限定）。
     *
     * @param tenantId  租户编码
     * @param deviceKey 设备标识
     * @return 设备；不存在为空
     */
    Mono<Device> findByKey(String tenantId, String deviceKey);

    /**
     * 按 ID 查询（租户限定）。
     *
     * @param tenantId 租户编码
     * @param id       主键
     * @return 设备；跨租户访问视为不存在
     */
    Mono<Device> findById(String tenantId, Long id);

    /**
     * 分页查询租户设备。
     *
     * @param tenantId  租户编码
     * @param status    状态（可空）
     * @param pageNum   页码（1 起）
     * @param pageSize  页大小
     * @return 分页结果
     */
    Mono<PageResponse<Device>> page(String tenantId, String status, int pageNum, int pageSize);

    /**
     * 租户内设备总数。
     *
     * @param tenantId 租户编码
     * @return 设备总数
     */
    Mono<Long> count(String tenantId);

    /**
     * 新增设备（租户内 deviceKey 唯一）。
     *
     * @param device 设备实体（含租户与 secretHash）
     * @return 落库后的设备
     */
    Mono<Device> insert(Device device);

    /**
     * 逻辑删除设备（租户限定）。
     *
     * @param tenantId 租户编码
     * @param id       主键
     * @throws com.apex.scaffold.common.exception.BusinessException 删除 0 行时以 {@code NOT_FOUND} 终止
     */
    Mono<Void> delete(String tenantId, Long id);

    /**
     * 刷新活跃状态（认证成功置 online，遥测上行刷新活跃时间）。
     *
     * @param tenantId  租户编码
     * @param deviceKey 设备标识
     * @param online    是否置为 online（false 仅刷新活跃时间）
     */
    Mono<Void> touchActivity(String tenantId, String deviceKey, boolean online);
}
