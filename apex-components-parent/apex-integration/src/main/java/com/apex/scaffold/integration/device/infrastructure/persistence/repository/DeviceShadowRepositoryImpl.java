package com.apex.scaffold.integration.device.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.integration.device.domain.DeviceShadow;
import com.apex.scaffold.integration.device.domain.DeviceShadowConflictException;
import com.apex.scaffold.integration.device.domain.repository.DeviceShadowRepository;
import com.apex.scaffold.integration.device.infrastructure.persistence.mapper.DeviceShadowMapper;
import com.apex.scaffold.integration.device.infrastructure.persistence.po.DeviceShadowPO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link DeviceShadowRepository} 的 MyBatis-Plus 实现（方案 M5 / 批次 E3）。
 *
 * <p>{@code reported} 更新采用条件更新（{@code WHERE tenant_id = ? AND device_key = ?
 * AND version = 期望版本}）并递增版本；更新 0 行即乐观锁冲突，抛
 * {@link DeviceShadowConflictException} 交由应用服务重读重试。
 * 阻塞 MyBatis 访问统一调度到 jdbc 池。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Repository
@RequiredArgsConstructor
public class DeviceShadowRepositoryImpl implements DeviceShadowRepository {

    private final DeviceShadowMapper mapper;

    @Override
    public Mono<DeviceShadow> findByKey(String tenantId, String deviceKey) {
        return Mono.fromCallable(() -> mapper.selectOne(ConditionUtil.lambda(DeviceShadowPO.class)
                        .eq(DeviceShadowPO::getTenantId, tenantId)
                        .eq(DeviceShadowPO::getDeviceKey, deviceKey)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(this::toEntity);
    }

    @Override
    public Mono<DeviceShadow> insert(DeviceShadow shadow) {
        return Mono.fromCallable(() -> {
                    DeviceShadowPO po = toPo(shadow);
                    if (po.getVersion() == null) {
                        po.setVersion(0);
                    }
                    try {
                        mapper.insert(po);
                    } catch (DuplicateKeyException e) {
                        // 设备注册先建影子，重复注册并发建影子时以设备侧唯一键报错为准
                        throw new com.apex.scaffold.common.exception.BusinessException(
                                com.apex.scaffold.common.result.ResultCode.BUSINESS_ERROR,
                                "设备影子已存在: " + po.getDeviceKey());
                    }
                    return po;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(this::toEntity);
    }

    @Override
    public Mono<DeviceShadow> updateReported(
            String tenantId, String deviceKey, String reportedJson, int expectedVersion) {
        return Mono.fromCallable(() -> {
                    LambdaUpdateWrapper<DeviceShadowPO> update = new LambdaUpdateWrapper<DeviceShadowPO>()
                            .set(DeviceShadowPO::getReported, reportedJson)
                            .set(DeviceShadowPO::getVersion, expectedVersion + 1)
                            .eq(DeviceShadowPO::getTenantId, tenantId)
                            .eq(DeviceShadowPO::getDeviceKey, deviceKey)
                            .eq(DeviceShadowPO::getVersion, expectedVersion);
                    int rows = mapper.update(null, update);
                    if (rows == 0) {
                        throw new DeviceShadowConflictException(deviceKey, expectedVersion);
                    }
                    return mapper.selectOne(ConditionUtil.lambda(DeviceShadowPO.class)
                            .eq(DeviceShadowPO::getTenantId, tenantId)
                            .eq(DeviceShadowPO::getDeviceKey, deviceKey));
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(this::toEntity);
    }

    private DeviceShadow toEntity(DeviceShadowPO po) {
        DeviceShadow shadow = new DeviceShadow();
        shadow.setId(po.getId());
        shadow.setTenantId(po.getTenantId());
        shadow.setDeviceKey(po.getDeviceKey());
        shadow.setDesired(po.getDesired());
        shadow.setReported(po.getReported());
        shadow.setVersion(po.getVersion());
        shadow.setCreateTime(po.getCreateTime());
        shadow.setUpdateTime(po.getUpdateTime());
        return shadow;
    }

    private DeviceShadowPO toPo(DeviceShadow shadow) {
        DeviceShadowPO po = new DeviceShadowPO();
        po.setId(shadow.getId());
        po.setTenantId(shadow.getTenantId());
        po.setDeviceKey(shadow.getDeviceKey());
        po.setDesired(shadow.getDesired());
        po.setReported(shadow.getReported());
        po.setVersion(shadow.getVersion());
        po.setCreateTime(shadow.getCreateTime());
        po.setUpdateTime(shadow.getUpdateTime());
        return po;
    }
}
