package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.TenantEntity;
import com.apex.scaffold.domain.repository.TenantRepository;
import com.apex.scaffold.infrastructure.persistence.converter.TenantPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.TenantMapper;
import com.apex.scaffold.infrastructure.persistence.po.TenantPO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link TenantRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link TenantEntity} 的持久化读写与租户编码唯一性校验，通过
 * {@link TenantPoConverter} 在领域实体与 {@link TenantPO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池执行。</p>
 *
 * <p>租户表 {@code sys_tenant} 为平台级元数据，已在
 * {@code DefaultTenantLineHandler.IGNORE_TABLES} 中排除，不受租户拦截器影响，
 * 因此本仓储查询不会注入租户条件，可跨租户由平台管理员管理。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see TenantRepository
 */
@Repository
@RequiredArgsConstructor
public class TenantRepositoryImpl implements TenantRepository {

    private final TenantMapper tenantMapper;
    private final TenantPoConverter tenantPoConverter;

    @Override
    public Mono<TenantEntity> findById(Long id) {
        return Mono.fromCallable(() -> tenantMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(tenantPoConverter.toEntity(po)));
    }

    @Override
    public Mono<TenantEntity> findByCode(String tenantCode) {
        return Mono.fromCallable(() -> tenantMapper.selectOne(
                        ConditionUtil.lambda(TenantPO.class).eq(TenantPO::getTenantCode, tenantCode)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(tenantPoConverter::toEntity);
    }

    @Override
    public Mono<List<TenantEntity>> findAll() {
        return Mono.fromCallable(() -> tenantMapper.selectList(
                        ConditionUtil.lambda(TenantPO.class).orderByAsc(TenantPO::getTenantCode)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(tenantPoConverter::toEntityList);
    }

    @Override
    public Mono<PageResponse<TenantEntity>> search(
            String tenantCode, String tenantName, String status, int pageNum, int pageSize) {
        return Mono.fromCallable(() -> {
                    Page<TenantPO> page = new Page<>(pageNum, pageSize);
                    tenantMapper.selectPage(
                            page,
                            ConditionUtil.lambda(TenantPO.class)
                                    .likeIfPresent(TenantPO::getTenantCode, tenantCode)
                                    .likeIfPresent(TenantPO::getTenantName, tenantName)
                                    .eqIfPresent(TenantPO::getStatus, status)
                                    .orderByAsc(TenantPO::getTenantCode));
                    List<TenantEntity> entities = tenantPoConverter.toEntityList(page.getRecords());
                    return PageResponse.of(entities, page.getTotal(), pageNum, pageSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Boolean> existsByCode(String tenantCode, Long excludeId) {
        return Mono.fromCallable(() -> {
                    Long count = tenantMapper.selectCount(ConditionUtil.lambda(TenantPO.class)
                            .eqIfPresent(TenantPO::getTenantCode, tenantCode)
                            .neIfPresent(TenantPO::getId, excludeId));
                    return count != null && count > 0;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> insert(TenantEntity entity) {
        return Mono.fromCallable(() -> {
                    TenantPO po = tenantPoConverter.toPo(entity);
                    int rows = tenantMapper.insert(po);
                    if (rows > 0) {
                        copyPersistenceFields(po, entity);
                    }
                    return rows;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> updateById(TenantEntity entity) {
        return Mono.fromCallable(() -> {
                    TenantPO po = tenantPoConverter.toPo(entity);
                    int rows = tenantMapper.updateById(po);
                    if (rows > 0) {
                        copyPersistenceFields(po, entity);
                    }
                    return rows;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    private void copyPersistenceFields(TenantPO po, TenantEntity entity) {
        entity.setId(po.getId());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        entity.setLockVersion(po.getLockVersion());
        entity.setDeleted(po.getDeleted());
    }

    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> tenantMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
