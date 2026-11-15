package com.apex.scaffold.integration.device.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.integration.device.domain.Device;
import com.apex.scaffold.integration.device.domain.repository.DeviceRepository;
import com.apex.scaffold.integration.device.infrastructure.persistence.mapper.DeviceMapper;
import com.apex.scaffold.integration.device.infrastructure.persistence.po.DevicePO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link DeviceRepository} 的 MyBatis-Plus 实现（方案 M5 / 批次 E3）。
 *
 * <p>全部查询显式携带 {@code tenant_id} 条件，叠加租户行级拦截器（开启时条件冗余但
 * 语义一致：上下文租户与显式租户不一致时结果为空，保持 fail-closed）。
 * 阻塞 MyBatis 访问统一调度到 jdbc 池。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Repository
@RequiredArgsConstructor
public class DeviceRepositoryImpl implements DeviceRepository {

    private final DeviceMapper mapper;

    @Override
    public Mono<Device> findByKey(String tenantId, String deviceKey) {
        return Mono.fromCallable(() -> mapper.selectOne(ConditionUtil.lambda(DevicePO.class)
                        .eq(DevicePO::getTenantId, tenantId)
                        .eq(DevicePO::getDeviceKey, deviceKey)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(this::toEntity);
    }

    @Override
    public Mono<Device> findById(String tenantId, Long id) {
        return Mono.fromCallable(() -> mapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .filter(po -> po != null && tenantId != null && tenantId.equals(po.getTenantId()))
                .map(this::toEntity);
    }

    @Override
    public Mono<PageResponse<Device>> page(String tenantId, String status, int pageNum, int pageSize) {
        return Mono.fromCallable(() -> TenantContext.callWithTenant(tenantId, () -> {
                    Page<DevicePO> page = new Page<>(pageNum, pageSize);
                    mapper.selectPage(
                            page,
                            ConditionUtil.lambda(DevicePO.class)
                                    .eqIfPresent(DevicePO::getStatus, status)
                                    .eq(DevicePO::getTenantId, tenantId)
                                    .orderByDesc(DevicePO::getCreateTime));
                    return PageResponse.of(
                            page.getRecords().stream().map(this::toEntity).toList(),
                            page.getTotal(),
                            pageNum,
                            pageSize);
                }))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Long> count(String tenantId) {
        return Mono.fromCallable(() -> {
                    // 统计可由内部端点以显式 tenantId 参数调用（无租户上下文），
                    // 在执行线程内绑定租户后再走 MP 通道，保证租户拦截器 fail-closed 语义可满足；
                    // 既有带上下文调用为同租户重设，行为不变。
                    String previous = TenantContext.setTenantId(tenantId);
                    try {
                        return mapper.selectCount(
                                ConditionUtil.lambda(DevicePO.class).eq(DevicePO::getTenantId, tenantId));
                    } finally {
                        TenantContext.restoreTenantId(previous);
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Device> insert(Device device) {
        return Mono.fromCallable(() -> {
                    DevicePO po = toPo(device);
                    if (po.getStatus() == null) {
                        po.setStatus(Device.STATUS_OFFLINE);
                    }
                    if (po.getDeleted() == null) {
                        po.setDeleted(0);
                    }
                    try {
                        mapper.insert(po);
                    } catch (DuplicateKeyException e) {
                        throw new BusinessException(ResultCode.BUSINESS_ERROR, "设备标识已存在: " + po.getDeviceKey());
                    }
                    return po;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(this::toEntity);
    }

    @Override
    public Mono<Void> delete(String tenantId, Long id) {
        return Mono.fromCallable(() -> mapper.delete(ConditionUtil.lambda(DevicePO.class)
                        .eq(DevicePO::getTenantId, tenantId)
                        .eq(DevicePO::getId, id)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(rows ->
                        rows > 0 ? Mono.empty() : Mono.error(new BusinessException(ResultCode.NOT_FOUND, "设备不存在")));
    }

    @Override
    public Mono<Void> touchActivity(String tenantId, String deviceKey, boolean online) {
        return Mono.fromCallable(() -> {
                    DevicePO patch = new DevicePO();
                    patch.setLastActiveAt(LocalDateTime.now());
                    if (online) {
                        patch.setStatus(Device.STATUS_ONLINE);
                    }
                    return mapper.update(
                            patch,
                            ConditionUtil.lambda(DevicePO.class)
                                    .eq(DevicePO::getTenantId, tenantId)
                                    .eq(DevicePO::getDeviceKey, deviceKey));
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }

    private Device toEntity(DevicePO po) {
        Device device = new Device();
        device.setId(po.getId());
        device.setTenantId(po.getTenantId());
        device.setDeviceKey(po.getDeviceKey());
        device.setSecretHash(po.getSecretHash());
        device.setCertFingerprint(po.getCertFingerprint());
        device.setStatus(po.getStatus());
        device.setFirmwareVersion(po.getFirmwareVersion());
        device.setLabels(po.getLabels());
        device.setLastActiveAt(po.getLastActiveAt());
        device.setCreateTime(po.getCreateTime());
        device.setUpdateTime(po.getUpdateTime());
        device.setDeleted(po.getDeleted());
        return device;
    }

    private DevicePO toPo(Device device) {
        DevicePO po = new DevicePO();
        po.setId(device.getId());
        po.setTenantId(device.getTenantId());
        po.setDeviceKey(device.getDeviceKey());
        po.setSecretHash(device.getSecretHash());
        po.setCertFingerprint(device.getCertFingerprint());
        po.setStatus(device.getStatus());
        po.setFirmwareVersion(device.getFirmwareVersion());
        po.setLabels(device.getLabels());
        po.setLastActiveAt(device.getLastActiveAt());
        po.setCreateTime(device.getCreateTime());
        po.setUpdateTime(device.getUpdateTime());
        po.setDeleted(device.getDeleted());
        return po;
    }
}
