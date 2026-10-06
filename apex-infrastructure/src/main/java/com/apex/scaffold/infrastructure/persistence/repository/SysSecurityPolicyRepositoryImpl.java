package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.SysSecurityPolicyEntity;
import com.apex.scaffold.domain.repository.SysSecurityPolicyRepository;
import com.apex.scaffold.infrastructure.persistence.converter.SysSecurityPolicyPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.SysSecurityPolicyMapper;
import com.apex.scaffold.infrastructure.persistence.po.SysSecurityPolicyPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link SysSecurityPolicyRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责系统安全策略的持久化读写与租户隔离查询，通过
 * {@link SysSecurityPolicyPoConverter} 在领域实体与 {@link SysSecurityPolicyPO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池执行。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>安全策略的增删改查</li>
 *   <li>按租户隔离查询安全策略</li>
 *   <li>JDBC 阻塞操作隔离到专用调度器</li>
 * </ul>
 *
 * <h2>租户隔离保障</h2>
 * <ul>
 *   <li>所有查询自动过滤 {@code tenantId} 和 {@code deleted} 字段</li>
 *   <li>更新和删除操作依赖 MyBatis-Plus 租户拦截器校验</li>
 *   <li>查询时返回最新的一条策略记录</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SysSecurityPolicyRepository
 */
@Repository
@RequiredArgsConstructor
public class SysSecurityPolicyRepositoryImpl implements SysSecurityPolicyRepository {

    private final SysSecurityPolicyMapper securityPolicyMapper;

    private final SysSecurityPolicyPoConverter securityPolicyPoConverter;

    @Override
    public Mono<SysSecurityPolicyEntity> findById(Long id) {
        return Mono.fromCallable(() -> securityPolicyMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(securityPolicyPoConverter.toEntity(po)));
    }

    @Override
    public Mono<SysSecurityPolicyEntity> findByTenantId(String tenantId) {
        return Mono.fromCallable(() -> securityPolicyMapper.selectOne(ConditionUtil.lambda(SysSecurityPolicyPO.class)
                        .eq(SysSecurityPolicyPO::getTenantId, tenantId)
                        .orderByDesc(SysSecurityPolicyPO::getCreateTime)
                        .last("LIMIT 1")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(securityPolicyPoConverter.toEntity(po)));
    }

    @Override
    public Mono<Integer> insert(SysSecurityPolicyEntity entity) {
        return Mono.fromCallable(() -> securityPolicyMapper.insert(securityPolicyPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> updateById(SysSecurityPolicyEntity entity) {
        return Mono.fromCallable(() -> securityPolicyMapper.updateById(securityPolicyPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> securityPolicyMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
