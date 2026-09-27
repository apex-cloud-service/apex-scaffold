package com.apex.scaffold.authcenter.infrastructure.persistence.repository;

import com.apex.scaffold.authcenter.domain.model.entity.TotpEntity;
import com.apex.scaffold.authcenter.domain.repository.TotpRepository;
import com.apex.scaffold.authcenter.infrastructure.persistence.converter.TotpConverter;
import com.apex.scaffold.authcenter.infrastructure.persistence.mapper.TotpMapper;
import com.apex.scaffold.authcenter.infrastructure.persistence.po.TotpPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * TOTP 仓储实现
 *
 * <p>基于 MyBatis-Plus 实现 TotpRepository 接口，使用响应式编程包装阻塞数据库操作。
 *
 * @author Apex Scaffold Team
 * @since 2026-09-21
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TotpRepositoryImpl implements TotpRepository {

    private final TotpMapper totpMapper;
    private final TotpConverter totpConverter;

    @Override
    public Mono<TotpEntity> save(TotpEntity totp) {
        return Mono.fromCallable(() -> {
                    TotpPO po = totpConverter.toPo(totp);
                    totpMapper.insert(po);
                    return totpConverter.toEntity(po);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<TotpEntity> update(TotpEntity totp) {
        return Mono.fromCallable(() -> {
                    TotpPO po = totpConverter.toPo(totp);
                    totpMapper.updateById(po);
                    return totpConverter.toEntity(po);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<TotpEntity> findById(Long id, String tenantId) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<TotpPO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(TotpPO::getId, id).eq(TotpPO::getTenantId, tenantId);
                    TotpPO po = totpMapper.selectOne(wrapper);
                    return po != null ? totpConverter.toEntity(po) : null;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<TotpEntity> findByUserId(Long userId, String tenantId) {
        return Mono.fromCallable(() -> {
                    TotpPO po = totpMapper.selectByUserIdAndTenantId(userId, tenantId);
                    return po != null ? totpConverter.toEntity(po) : null;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> deleteById(Long id, String tenantId) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<TotpPO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(TotpPO::getId, id).eq(TotpPO::getTenantId, tenantId);
                    return totpMapper.delete(wrapper) > 0;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> deleteByUserId(Long userId, String tenantId) {
        return Mono.fromCallable(() -> totpMapper.deleteByUserIdAndTenantId(userId, tenantId) > 0)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> existsByUserId(Long userId, String tenantId) {
        return Mono.fromCallable(() -> totpMapper.existsByUserIdAndTenantId(userId, tenantId) > 0)
                .subscribeOn(Schedulers.boundedElastic());
    }
}
