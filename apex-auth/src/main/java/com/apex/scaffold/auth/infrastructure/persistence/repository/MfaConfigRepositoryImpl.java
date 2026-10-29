package com.apex.scaffold.auth.infrastructure.persistence.repository;

import com.apex.scaffold.auth.domain.model.entity.MfaConfigEntity;
import com.apex.scaffold.auth.domain.repository.MfaConfigRepository;
import com.apex.scaffold.auth.infrastructure.persistence.converter.MfaConfigConverter;
import com.apex.scaffold.auth.infrastructure.persistence.mapper.MfaConfigMapper;
import com.apex.scaffold.auth.infrastructure.persistence.po.MfaConfigPO;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * MFA 配置仓储实现
 *
 * <p>基于 MyBatis-Plus 实现 MfaConfigRepository 接口，使用响应式编程包装阻塞数据库操作。
 * 阻塞调用统一隔离到 {@link BlockingIoSchedulers#jdbc()}，不使用全局 {@code boundedElastic}，
 * 避免 MFA 查询挤占其它阻塞任务的线程预算。</p>
 *
 * @author Apex Scaffold Team
 * @since 1.3.1
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MfaConfigRepositoryImpl implements MfaConfigRepository {

    private final MfaConfigMapper mfaConfigMapper;
    private final MfaConfigConverter mfaConfigConverter;

    @Override
    public Mono<MfaConfigEntity> save(MfaConfigEntity config) {
        return Mono.fromCallable(() -> {
                    MfaConfigPO po = mfaConfigConverter.toPo(config);
                    mfaConfigMapper.insert(po);
                    return mfaConfigConverter.toEntity(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<MfaConfigEntity> update(MfaConfigEntity config) {
        return Mono.fromCallable(() -> {
                    MfaConfigPO po = mfaConfigConverter.toPo(config);
                    mfaConfigMapper.updateById(po);
                    return mfaConfigConverter.toEntity(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<MfaConfigEntity> findById(Long id, String tenantId) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<MfaConfigPO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(MfaConfigPO::getId, id).eq(MfaConfigPO::getTenantId, tenantId);
                    MfaConfigPO po = mfaConfigMapper.selectOne(wrapper);
                    return po != null ? mfaConfigConverter.toEntity(po) : null;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Flux<MfaConfigEntity> findByUserId(Long userId, String tenantId) {
        return Mono.fromCallable(() -> {
                    List<MfaConfigPO> list = mfaConfigMapper.selectByUserIdAndTenantId(userId, tenantId);
                    return list.stream().map(mfaConfigConverter::toEntity).toList();
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<MfaConfigEntity> findByUserIdAndType(Long userId, String mfaType, String tenantId) {
        return Mono.fromCallable(() -> {
                    MfaConfigPO po = mfaConfigMapper.selectByUserIdAndTypeAndTenantId(userId, mfaType, tenantId);
                    return po != null ? mfaConfigConverter.toEntity(po) : null;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Flux<MfaConfigEntity> findEnabledByUserId(Long userId, String tenantId) {
        return Mono.fromCallable(() -> {
                    List<MfaConfigPO> list = mfaConfigMapper.selectEnabledByUserIdAndTenantId(userId, tenantId);
                    return list.stream().map(mfaConfigConverter::toEntity).toList();
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<MfaConfigEntity> findRequiredByUserId(Long userId, String tenantId) {
        return Mono.fromCallable(() -> {
                    List<MfaConfigPO> list = mfaConfigMapper.selectRequiredByUserIdAndTenantId(userId, tenantId);
                    return list.stream().map(mfaConfigConverter::toEntity).toList();
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Boolean> deleteById(Long id, String tenantId) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<MfaConfigPO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(MfaConfigPO::getId, id).eq(MfaConfigPO::getTenantId, tenantId);
                    return mfaConfigMapper.delete(wrapper) > 0;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Boolean> deleteByUserId(Long userId, String tenantId) {
        return Mono.fromCallable(() -> mfaConfigMapper.deleteByUserIdAndTenantId(userId, tenantId) > 0)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Boolean> existsByUserIdAndType(Long userId, String mfaType, String tenantId) {
        return Mono.fromCallable(() -> mfaConfigMapper.existsByUserIdAndTypeAndTenantId(userId, mfaType, tenantId) > 0)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
