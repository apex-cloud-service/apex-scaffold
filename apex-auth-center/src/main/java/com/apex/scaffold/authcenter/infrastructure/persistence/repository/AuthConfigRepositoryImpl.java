package com.apex.scaffold.authcenter.infrastructure.persistence.repository;

import com.apex.scaffold.authcenter.domain.model.entity.AuthConfigEntity;
import com.apex.scaffold.authcenter.domain.repository.AuthConfigRepository;
import com.apex.scaffold.authcenter.infrastructure.persistence.converter.AuthConfigConverter;
import com.apex.scaffold.authcenter.infrastructure.persistence.mapper.AuthConfigMapper;
import com.apex.scaffold.authcenter.infrastructure.persistence.po.AuthConfigPO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 在可信租户和 JDBC 调度器中执行认证配置持久化。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class AuthConfigRepositoryImpl implements AuthConfigRepository {
    private final AuthConfigMapper mapper;
    private final AuthConfigConverter converter;

    @Override
    public Mono<AuthConfigEntity> save(AuthConfigEntity entity) {
        return JdbcTenantScope.call(entity.getTenantId(), tenant -> {
            AuthConfigPO po = converter.toPo(entity);
            po.setTenantId(tenant);
            JdbcTenantScope.changed(mapper.insert(po));
            return converter.toEntity(po);
        });
    }

    @Override
    public Mono<AuthConfigEntity> update(AuthConfigEntity entity) {
        return JdbcTenantScope.call(entity.getTenantId(), tenant -> {
            AuthConfigPO po = converter.toPo(entity);
            JdbcTenantScope.changed(mapper.update(
                    po, JdbcTenantScope.<AuthConfigPO>update(tenant).eq("id", entity.getId())));
            return converter.toEntity(
                    mapper.selectOne(JdbcTenantScope.<AuthConfigPO>query(tenant).eq("id", entity.getId())));
        });
    }

    @Override
    public Mono<Void> deleteById(Long id, String tenantId) {
        return JdbcTenantScope.call(
                        tenantId,
                        tenant -> mapper.delete(
                                JdbcTenantScope.<AuthConfigPO>query(tenant).eq("id", id)))
                .then();
    }

    private Flux<AuthConfigEntity> query(String tenantId, Consumer<QueryWrapper<AuthConfigPO>> filter) {
        return JdbcTenantScope.call(tenantId, tenant -> {
                    QueryWrapper<AuthConfigPO> query = JdbcTenantScope.query(tenant);
                    filter.accept(query);
                    return mapper.selectList(query);
                })
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Mono<AuthConfigEntity> findById(Long id, String tenantId) {
        return query(tenantId, q -> q.eq("id", id)).singleOrEmpty();
    }

    @Override
    public Mono<AuthConfigEntity> findByTypeAndKey(String type, String key, String tenantId) {
        return query(tenantId, q -> q.eq("config_type", type).eq("config_key", key))
                .singleOrEmpty();
    }

    @Override
    public Flux<AuthConfigEntity> findByType(String type, String tenantId) {
        return query(tenantId, q -> q.eq("config_type", type));
    }

    @Override
    public Flux<AuthConfigEntity> findAllEnabled(String tenantId) {
        return query(tenantId, q -> q.eq("enabled", true));
    }

    @Override
    public Flux<AuthConfigEntity> findAll(String tenantId) {
        return query(tenantId, q -> {});
    }

    @Override
    public Flux<AuthConfigEntity> findDefaultConfigs() {
        return query(null, q -> q.eq("is_default", true));
    }

    @Override
    public Flux<AuthConfigEntity> findEnabledByType(String type, String tenantId) {
        return query(tenantId, q -> q.eq("config_type", type).eq("enabled", true));
    }

    @Override
    public Mono<Boolean> existsByTypeAndKey(String type, String key, String tenantId) {
        return findByTypeAndKey(type, key, tenantId).hasElement();
    }

    @Override
    public Mono<Long> batchSave(Flux<AuthConfigEntity> configs) {
        return configs.concatMap(this::save).count();
    }

    @Override
    public Mono<Long> countByType(String type, String tenantId) {
        return JdbcTenantScope.call(
                tenantId,
                tenant -> mapper.selectCount(
                        JdbcTenantScope.<AuthConfigPO>query(tenant).eq("config_type", type)));
    }
}
