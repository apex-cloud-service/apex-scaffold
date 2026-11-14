package com.apex.scaffold.integration.device.domain.repository;

import com.apex.scaffold.integration.device.domain.DeviceShadow;
import reactor.core.publisher.Mono;

/**
 * 设备影子仓储端口（方案 M5 / 批次 E3）。
 *
 * <p>影子按（租户 + 设备）唯一；{@code reported} 更新以 {@code version} 乐观锁提交，
 * 冲突抛 {@link com.apex.scaffold.integration.device.domain.DeviceShadowConflictException}，
 * 由应用服务有限次重读重试。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface DeviceShadowRepository {

    /**
     * 按设备标识查询影子（租户限定）。
     *
     * @param tenantId  租户编码
     * @param deviceKey 设备标识
     * @return 影子；不存在为空
     */
    Mono<DeviceShadow> findByKey(String tenantId, String deviceKey);

    /**
     * 新增影子（租户内 deviceKey 唯一）。
     *
     * @param shadow 影子实体（含租户）
     * @return 落库后的影子
     */
    Mono<DeviceShadow> insert(DeviceShadow shadow);

    /**
     * 以乐观锁更新 {@code reported}（版本不符抛冲突异常）。
     *
     * @param tenantId        租户编码
     * @param deviceKey       设备标识
     * @param reportedJson    新 reported JSON
     * @param expectedVersion 期望版本
     * @return 更新后的影子（版本已递增）
     */
    Mono<DeviceShadow> updateReported(String tenantId, String deviceKey, String reportedJson, int expectedVersion);
}
