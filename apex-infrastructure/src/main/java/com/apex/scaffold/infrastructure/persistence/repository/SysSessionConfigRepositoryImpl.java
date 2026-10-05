package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.SysSessionConfigEntity;
import com.apex.scaffold.domain.repository.SysSessionConfigRepository;
import com.apex.scaffold.infrastructure.persistence.converter.SysSessionConfigPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.SysSessionConfigMapper;
import com.apex.scaffold.infrastructure.persistence.po.SysSessionConfigPO;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link SysSessionConfigRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责系统会话配置的持久化读写与租户隔离查询，通过
 * {@link SysSessionConfigPoConverter} 在领域实体与 {@link SysSessionConfigPO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池执行。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>会话配置的增删改查</li>
 *   <li>按租户隔离查询会话配置</li>
 *   <li>JDBC 阻塞操作隔离到专用调度器</li>
 * </ul>
 *
 * <h2>租户隔离保障</h2>
 * <ul>
 *   <li>所有查询自动过滤 {@code tenantId} 和 {@code deleted} 字段</li>
 *   <li>更新和删除操作依赖 MyBatis-Plus 租户拦截器校验</li>
 *   <li>查询时返回最新的一条配置记录</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SysSessionConfigRepository
 */
@Repository
@RequiredArgsConstructor
public class SysSessionConfigRepositoryImpl implements SysSessionConfigRepository {

    private final SysSessionConfigMapper sessionConfigMapper;

    private final SysSessionConfigPoConverter sessionConfigPoConverter;

    @Override
    public Mono<SysSessionConfigEntity> findById(Long id) {
        return Mono.fromCallable(() -> sessionConfigMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(sessionConfigPoConverter.toEntity(po)));
    }

    @Override
    public Mono<SysSessionConfigEntity> findByTenantId(String tenantId) {
        return Mono.fromCallable(() -> sessionConfigMapper.selectOne(ConditionUtil.lambda(SysSessionConfigPO.class)
                        .eq(SysSessionConfigPO::getTenantId, tenantId)
                        .orderByDesc(SysSessionConfigPO::getCreateTime)
                        .last("LIMIT 1")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(sessionConfigPoConverter.toEntity(po)));
    }

    @Override
    public Mono<Integer> insert(SysSessionConfigEntity entity) {
        return Mono.fromCallable(() -> sessionConfigMapper.insert(sessionConfigPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> updateById(SysSessionConfigEntity entity) {
        return Mono.fromCallable(() -> sessionConfigMapper.updateById(sessionConfigPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> sessionConfigMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
