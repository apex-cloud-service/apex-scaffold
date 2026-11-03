package com.apex.scaffold.integration.device.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.integration.device.domain.DeviceCommand;
import com.apex.scaffold.integration.device.domain.repository.DeviceCommandRepository;
import com.apex.scaffold.integration.device.infrastructure.persistence.mapper.DeviceCommandMapper;
import com.apex.scaffold.integration.device.infrastructure.persistence.po.DeviceCommandPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link DeviceCommandRepository} 的 MyBatis-Plus 实现（方案 M5 / 批次 E3）。
 *
 * <p>状态迁移全部为条件更新（仅从预期状态迁出并刷新时间戳），0 行更新返回
 * {@code false} 表示状态已推进或命令不存在，不做二次解释。阻塞 MyBatis 访问统一
 * 调度到 jdbc 池。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Repository
@RequiredArgsConstructor
public class DeviceCommandRepositoryImpl implements DeviceCommandRepository {

    private final DeviceCommandMapper mapper;

    @Override
    public Mono<PageResponse<DeviceCommand>> pageByDevice(
            String tenantId, String deviceKey, int pageNum, int pageSize) {
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.min(Math.max(1, pageSize), 100);
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<DeviceCommandPO> wrapper = ConditionUtil.lambda(DeviceCommandPO.class)
                            .eq(DeviceCommandPO::getTenantId, tenantId)
                            .eq(DeviceCommandPO::getDeviceKey, deviceKey);
                    Long total = mapper.selectCount(wrapper);
                    wrapper.orderByDesc(DeviceCommandPO::getId)
                            .last("LIMIT " + safePageSize + " OFFSET " + (long) (safePageNum - 1) * safePageSize);
                    List<DeviceCommand> records = mapper.selectList(wrapper).stream()
                            .map(this::toEntity)
                            .toList();
                    return PageResponse.of(records, total == null ? 0L : total, safePageNum, safePageSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<DeviceCommand> findByCommandId(String tenantId, String commandId) {
        return Mono.fromCallable(() -> mapper.selectOne(ConditionUtil.lambda(DeviceCommandPO.class)
                        .eq(DeviceCommandPO::getTenantId, tenantId)
                        .eq(DeviceCommandPO::getCommandId, commandId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(this::toEntity);
    }

    @Override
    public Mono<DeviceCommand> insert(DeviceCommand command) {
        return Mono.fromCallable(() -> {
                    DeviceCommandPO po = toPo(command);
                    if (po.getStatus() == null) {
                        po.setStatus(DeviceCommand.STATUS_PENDING);
                    }
                    try {
                        mapper.insert(po);
                    } catch (DuplicateKeyException e) {
                        throw new com.apex.scaffold.common.exception.BusinessException(
                                com.apex.scaffold.common.result.ResultCode.BUSINESS_ERROR,
                                "命令 ID 已存在: " + po.getCommandId());
                    }
                    return po;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(this::toEntity);
    }

    @Override
    public Mono<Boolean> markSent(String tenantId, String commandId) {
        return Mono.fromCallable(() -> mapper.update(null, sentUpdate(tenantId, commandId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(rows -> rows > 0);
    }

    @Override
    public Mono<Boolean> markAcked(String tenantId, String commandId) {
        return Mono.fromCallable(() -> mapper.update(
                        null,
                        new LambdaUpdateWrapper<DeviceCommandPO>()
                                .set(DeviceCommandPO::getStatus, DeviceCommand.STATUS_ACK)
                                .set(DeviceCommandPO::getAckedAt, LocalDateTime.now())
                                .eq(DeviceCommandPO::getTenantId, tenantId)
                                .eq(DeviceCommandPO::getCommandId, commandId)
                                .in(
                                        DeviceCommandPO::getStatus,
                                        List.of(DeviceCommand.STATUS_PENDING, DeviceCommand.STATUS_SENT))))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(rows -> rows > 0);
    }

    @Override
    public Mono<Boolean> markTimeout(DeviceCommand command) {
        return Mono.fromCallable(() -> mapper.update(
                        null,
                        new LambdaUpdateWrapper<DeviceCommandPO>()
                                .set(DeviceCommandPO::getStatus, DeviceCommand.STATUS_TIMEOUT)
                                .eq(DeviceCommandPO::getTenantId, command.getTenantId())
                                .eq(DeviceCommandPO::getCommandId, command.getCommandId())
                                .in(
                                        DeviceCommandPO::getStatus,
                                        List.of(DeviceCommand.STATUS_PENDING, DeviceCommand.STATUS_SENT))))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(rows -> rows > 0);
    }

    @Override
    public Flux<DeviceCommand> findDueTimeout(int limit) {
        return Mono.fromCallable(() -> mapper
                        .selectList(ConditionUtil.lambda(DeviceCommandPO.class)
                                .in(
                                        DeviceCommandPO::getStatus,
                                        List.of(DeviceCommand.STATUS_PENDING, DeviceCommand.STATUS_SENT))
                                .le(DeviceCommandPO::getTimeoutAt, LocalDateTime.now())
                                .last("LIMIT " + Math.max(1, limit)))
                        .stream()
                        .map(this::toEntity)
                        .toList())
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapIterable(list -> list);
    }

    private LambdaUpdateWrapper<DeviceCommandPO> sentUpdate(String tenantId, String commandId) {
        return new LambdaUpdateWrapper<DeviceCommandPO>()
                .set(DeviceCommandPO::getStatus, DeviceCommand.STATUS_SENT)
                .set(DeviceCommandPO::getSentAt, LocalDateTime.now())
                .eq(DeviceCommandPO::getTenantId, tenantId)
                .eq(DeviceCommandPO::getCommandId, commandId)
                .eq(DeviceCommandPO::getStatus, DeviceCommand.STATUS_PENDING);
    }

    private DeviceCommand toEntity(DeviceCommandPO po) {
        DeviceCommand command = new DeviceCommand();
        command.setId(po.getId());
        command.setTenantId(po.getTenantId());
        command.setCommandId(po.getCommandId());
        command.setDeviceKey(po.getDeviceKey());
        command.setPayload(po.getPayload());
        command.setStatus(po.getStatus());
        command.setSentAt(po.getSentAt());
        command.setAckedAt(po.getAckedAt());
        command.setTimeoutAt(po.getTimeoutAt());
        command.setCreateTime(po.getCreateTime());
        command.setUpdateTime(po.getUpdateTime());
        return command;
    }

    private DeviceCommandPO toPo(DeviceCommand command) {
        DeviceCommandPO po = new DeviceCommandPO();
        po.setId(command.getId());
        po.setTenantId(command.getTenantId());
        po.setCommandId(command.getCommandId());
        po.setDeviceKey(command.getDeviceKey());
        po.setPayload(command.getPayload());
        po.setStatus(command.getStatus());
        po.setSentAt(command.getSentAt());
        po.setAckedAt(command.getAckedAt());
        po.setTimeoutAt(command.getTimeoutAt());
        po.setCreateTime(command.getCreateTime());
        po.setUpdateTime(command.getUpdateTime());
        return po;
    }
}
