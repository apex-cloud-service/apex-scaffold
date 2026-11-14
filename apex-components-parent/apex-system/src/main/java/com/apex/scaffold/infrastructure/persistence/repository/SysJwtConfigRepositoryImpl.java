package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.SysJwtConfigEntity;
import com.apex.scaffold.domain.repository.SysJwtConfigRepository;
import com.apex.scaffold.infrastructure.persistence.converter.SysJwtConfigPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.SysJwtConfigMapper;
import com.apex.scaffold.infrastructure.persistence.po.SysJwtConfigPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link SysJwtConfigRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责系统 JWT 配置的持久化读写与租户隔离查询，通过
 * {@link SysJwtConfigPoConverter} 在领域实体与 {@link SysJwtConfigPO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池执行。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>JWT 配置的增删改查</li>
 *   <li>按租户隔离查询 JWT 配置</li>
 *   <li>JDBC 阻塞操作隔离到专用调度器</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>所有查询自动过滤 {@code tenantId} 和 {@code deleted} 字段</li>
 *   <li>更新和删除操作依赖 MyBatis-Plus 租户拦截器校验</li>
 *   <li>查询时返回最新的一条配置记录</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>敏感字段（secretKey、privateKey）在持久化前必须已加密</li>
 *   <li>查询结果中的敏感字段保持加密状态</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SysJwtConfigRepository
 */
@Repository
@RequiredArgsConstructor
public class SysJwtConfigRepositoryImpl implements SysJwtConfigRepository {

    private final SysJwtConfigMapper jwtConfigMapper;

    private final SysJwtConfigPoConverter jwtConfigPoConverter;

    @Override
    public Mono<SysJwtConfigEntity> findById(Long id) {
        return Mono.fromCallable(() -> jwtConfigMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(jwtConfigPoConverter.toEntity(po)));
    }

    @Override
    public Mono<SysJwtConfigEntity> findByTenantId(String tenantId) {
        return Mono.fromCallable(() -> jwtConfigMapper.selectOne(ConditionUtil.lambda(SysJwtConfigPO.class)
                        .eq(SysJwtConfigPO::getTenantId, tenantId)
                        .orderByDesc(SysJwtConfigPO::getCreateTime)
                        .last("LIMIT 1")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(jwtConfigPoConverter.toEntity(po)));
    }

    @Override
    public Mono<Integer> insert(SysJwtConfigEntity entity) {
        return Mono.fromCallable(() -> jwtConfigMapper.insert(jwtConfigPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> updateById(SysJwtConfigEntity entity) {
        return Mono.fromCallable(() -> jwtConfigMapper.updateById(jwtConfigPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> jwtConfigMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
