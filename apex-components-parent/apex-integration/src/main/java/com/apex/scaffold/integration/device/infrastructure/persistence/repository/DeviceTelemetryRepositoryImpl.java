package com.apex.scaffold.integration.device.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.integration.device.domain.DeviceTelemetry;
import com.apex.scaffold.integration.device.domain.repository.DeviceTelemetryRepository;
import com.apex.scaffold.integration.device.infrastructure.persistence.mapper.DeviceTelemetryMapper;
import com.apex.scaffold.integration.device.infrastructure.persistence.po.DeviceTelemetryPO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link DeviceTelemetryRepository} 的 MyBatis-Plus 实现（方案 M5 / 批次 E3）。
 *
 * <p>批量落库在单次 jdbc 池任务内循环插入（一条 jdbc 访问边界，摊薄调度开销）；
 * 全部查询显式携带 {@code tenant_id} 条件，叠加租户行级拦截器。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Repository
@RequiredArgsConstructor
public class DeviceTelemetryRepositoryImpl implements DeviceTelemetryRepository {

    private final DeviceTelemetryMapper mapper;

    @Override
    public Mono<Void> insertAll(List<DeviceTelemetry> records) {
        return Mono.fromCallable(() -> {
                    for (DeviceTelemetry record : records) {
                        DeviceTelemetryPO po = toPo(record);
                        mapper.insert(po);
                        record.setId(po.getId());
                    }
                    return true;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }

    @Override
    public Mono<PageResponse<DeviceTelemetry>> page(
            String tenantId, Long deviceId, String deviceKey, int pageNum, int pageSize) {
        return Mono.fromCallable(() -> TenantContext.callWithTenant(tenantId, () -> {
                    Page<DeviceTelemetryPO> page = new Page<>(pageNum, pageSize);
                    mapper.selectPage(
                            page,
                            ConditionUtil.lambda(DeviceTelemetryPO.class)
                                    .eqIfPresent(DeviceTelemetryPO::getDeviceKey, deviceKey)
                                    .eq(DeviceTelemetryPO::getTenantId, tenantId)
                                    .orderByDesc(DeviceTelemetryPO::getRecordedAt));
                    return PageResponse.of(
                            page.getRecords().stream().map(this::toEntity).toList(),
                            page.getTotal(),
                            pageNum,
                            pageSize);
                }))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    private DeviceTelemetry toEntity(DeviceTelemetryPO po) {
        DeviceTelemetry telemetry = new DeviceTelemetry();
        telemetry.setId(po.getId());
        telemetry.setTenantId(po.getTenantId());
        telemetry.setDeviceKey(po.getDeviceKey());
        telemetry.setMetric(po.getMetric());
        telemetry.setRecordedAt(po.getRecordedAt());
        telemetry.setCreateTime(po.getCreateTime());
        return telemetry;
    }

    private DeviceTelemetryPO toPo(DeviceTelemetry telemetry) {
        DeviceTelemetryPO po = new DeviceTelemetryPO();
        po.setId(telemetry.getId());
        po.setTenantId(telemetry.getTenantId());
        po.setDeviceKey(telemetry.getDeviceKey());
        po.setMetric(telemetry.getMetric());
        po.setRecordedAt(telemetry.getRecordedAt());
        po.setCreateTime(telemetry.getCreateTime());
        return po;
    }
}
