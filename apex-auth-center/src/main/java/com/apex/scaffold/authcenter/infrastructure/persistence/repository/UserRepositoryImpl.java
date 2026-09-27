package com.apex.scaffold.authcenter.infrastructure.persistence.repository;

import com.apex.scaffold.authcenter.domain.model.entity.UserEntity;
import com.apex.scaffold.authcenter.domain.repository.UserRepository;
import com.apex.scaffold.authcenter.infrastructure.persistence.converter.UserConverter;
import com.apex.scaffold.authcenter.infrastructure.persistence.mapper.*;
import com.apex.scaffold.authcenter.infrastructure.persistence.po.*;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 租户隔离的认证仓储实现。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    private final UserMapper mapper;
    private final UserConverter converter;

    @Override
    public Mono<UserEntity> save(UserEntity entity) {
        return JdbcTenantScope.call(entity.getTenantId(), t -> {
            UserPO po = converter.toPo(entity);
            po.setTenantId(t);
            po.setCreatedAt(LocalDateTime.now());
            po.setUpdatedAt(po.getCreatedAt());
            po.setDeleted(0);
            if (po.getStatus() == null) {
                po.setStatus(1);
            }
            if (po.getFailedLoginAttempts() == null) {
                po.setFailedLoginAttempts(0);
            }
            JdbcTenantScope.changed(mapper.insert(po));
            return converter.toEntity(po);
        });
    }

    @Override
    public Mono<UserEntity> findById(Long id) {
        return JdbcTenantScope.call(
                null,
                t -> converter.toEntity(
                        mapper.selectOne(JdbcTenantScope.<UserPO>query(t).eq("id", id))));
    }

    @Override
    public Mono<UserEntity> update(UserEntity entity) {
        return JdbcTenantScope.call(entity.getTenantId(), t -> {
            if (entity.getId() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR);
            }
            UserPO po = converter.toPo(entity);
            po.setTenantId(t);
            po.setCreatedAt(null);
            po.setUpdatedAt(LocalDateTime.now());
            var query = JdbcTenantScope.<UserPO>update(t).eq("id", po.getId());

            JdbcTenantScope.changed(mapper.update(po, query));
            return converter.toEntity(
                    mapper.selectOne(JdbcTenantScope.<UserPO>query(t).eq("id", po.getId())));
        });
    }

    @Override
    public Mono<Boolean> deleteById(Long id) {
        return JdbcTenantScope.call(
                null, t -> mapper.delete(JdbcTenantScope.<UserPO>query(t).eq("id", id)) > 0);
    }

    @Override
    public Mono<UserEntity> findByUsername(String username, String tenantId) {
        return JdbcTenantScope.call(
                tenantId,
                t -> converter.toEntity(
                        mapper.selectOne(JdbcTenantScope.<UserPO>query(t).eq("username", username))));
    }

    @Override
    public Mono<Boolean> existsByUsername(String username, String tenantId) {
        return JdbcTenantScope.call(
                tenantId,
                t -> mapper.selectCount(JdbcTenantScope.<UserPO>query(t).eq("username", username)) > 0);
    }

    @Override
    public Mono<UserEntity> findByEmail(String email, String tenantId) {
        return JdbcTenantScope.call(
                tenantId,
                t -> converter.toEntity(
                        mapper.selectOne(JdbcTenantScope.<UserPO>query(t).eq("email", email))));
    }

    @Override
    public Mono<Boolean> existsByEmail(String email, String tenantId) {
        return JdbcTenantScope.call(
                tenantId,
                t -> mapper.selectCount(JdbcTenantScope.<UserPO>query(t).eq("email", email)) > 0);
    }

    @Override
    public Mono<UserEntity> findByPhone(String phone, String tenantId) {
        return JdbcTenantScope.call(
                tenantId,
                t -> converter.toEntity(
                        mapper.selectOne(JdbcTenantScope.<UserPO>query(t).eq("phone", phone))));
    }

    @Override
    public Mono<Boolean> existsByPhone(String phone, String tenantId) {
        return JdbcTenantScope.call(
                tenantId,
                t -> mapper.selectCount(JdbcTenantScope.<UserPO>query(t).eq("phone", phone)) > 0);
    }

    @Override
    public Flux<UserEntity> findByTenantId(String tenantId) {
        return JdbcTenantScope.call(tenantId, t -> mapper.selectList(JdbcTenantScope.<UserPO>query(t)))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }
}
