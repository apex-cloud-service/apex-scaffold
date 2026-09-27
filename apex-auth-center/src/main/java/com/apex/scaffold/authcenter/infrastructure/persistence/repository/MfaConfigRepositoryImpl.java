package com.apex.scaffold.authcenter.infrastructure.persistence.repository;

import com.apex.scaffold.authcenter.domain.model.entity.MfaConfigEntity;
import com.apex.scaffold.authcenter.domain.repository.MfaConfigRepository;
import com.apex.scaffold.authcenter.infrastructure.persistence.converter.MfaConfigConverter;
import com.apex.scaffold.authcenter.infrastructure.persistence.mapper.MfaConfigMapper;
import com.apex.scaffold.authcenter.infrastructure.persistence.po.MfaConfigPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * MFA 配置仓储实现
 *
 * <p>基于 MyBatis-Plus 实现 MfaConfigRepository 接口，使用响应式编程包装阻塞数据库操作。
 *
 * @author Apex Scaffold Team
 * @since 2026-09-21
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
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<MfaConfigEntity> update(MfaConfigEntity config) {
        return Mono.fromCallable(() -> {
                    MfaConfigPO po = mfaConfigConverter.toPo(config);
                    mfaConfigMapper.updateById(po);
                    return mfaConfigConverter.toEntity(po);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<MfaConfigEntity> findById(Long id, String tenantId) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<MfaConfigPO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(MfaConfigPO::getId, id).eq(MfaConfigPO::getTenantId, tenantId);
                    MfaConfigPO po = mfaConfigMapper.selectOne(wrapper);
                    return po != null ? mfaConfigConverter.toEntity(po) : null;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<MfaConfigEntity> findByUserId(Long userId, String tenantId) {
        return Mono.fromCallable(() -> {
                    List<MfaConfigPO> list = mfaConfigMapper.selectByUserIdAndTenantId(userId, tenantId);
                    return list.stream().map(mfaConfigConverter::toEntity).toList();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<MfaConfigEntity> findByUserIdAndType(Long userId, String mfaType, String tenantId) {
        return Mono.fromCallable(() -> {
                    MfaConfigPO po = mfaConfigMapper.selectByUserIdAndTypeAndTenantId(userId, mfaType, tenantId);
                    return po != null ? mfaConfigConverter.toEntity(po) : null;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<MfaConfigEntity> findEnabledByUserId(Long userId, String tenantId) {
        return Mono.fromCallable(() -> {
                    List<MfaConfigPO> list = mfaConfigMapper.selectEnabledByUserIdAndTenantId(userId, tenantId);
                    return list.stream().map(mfaConfigConverter::toEntity).toList();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<MfaConfigEntity> findRequiredByUserId(Long userId, String tenantId) {
        return Mono.fromCallable(() -> {
                    List<MfaConfigPO> list = mfaConfigMapper.selectRequiredByUserIdAndTenantId(userId, tenantId);
                    return list.stream().map(mfaConfigConverter::toEntity).toList();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Boolean> deleteById(Long id, String tenantId) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<MfaConfigPO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(MfaConfigPO::getId, id).eq(MfaConfigPO::getTenantId, tenantId);
                    return mfaConfigMapper.delete(wrapper) > 0;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> deleteByUserId(Long userId, String tenantId) {
        return Mono.fromCallable(() -> mfaConfigMapper.deleteByUserIdAndTenantId(userId, tenantId) > 0)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> existsByUserIdAndType(Long userId, String mfaType, String tenantId) {
        return Mono.fromCallable(() -> mfaConfigMapper.existsByUserIdAndTypeAndTenantId(userId, mfaType, tenantId) > 0)
                .subscribeOn(Schedulers.boundedElastic());
    }
}
