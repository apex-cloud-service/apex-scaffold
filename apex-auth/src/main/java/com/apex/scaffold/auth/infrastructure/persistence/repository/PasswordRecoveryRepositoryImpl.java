package com.apex.scaffold.auth.infrastructure.persistence.repository;

import com.apex.scaffold.auth.domain.model.entity.PasswordRecoveryEntity;
import com.apex.scaffold.auth.domain.repository.PasswordRecoveryRepository;
import com.apex.scaffold.auth.infrastructure.persistence.mapper.PasswordRecoveryMapper;
import com.apex.scaffold.auth.infrastructure.persistence.po.PasswordRecoveryPO;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 密码找回恢复仓储实现。
 *
 * <p>恢复链路是匿名跨租户的可信服务端流程：按恢复单主键与显式条件访问，
 * 不经过租户作用域包装（客户端无法选择或影响目标租户）；同步 JDBC 统一隔离到 jdbc 池。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class PasswordRecoveryRepositoryImpl implements PasswordRecoveryRepository {

    private final PasswordRecoveryMapper mapper;

    @Override
    public Mono<PasswordRecoveryEntity> save(PasswordRecoveryEntity entity) {
        return Mono.fromCallable(() -> {
                    PasswordRecoveryPO po = toPO(entity);
                    mapper.insert(po);
                    return toEntity(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<PasswordRecoveryEntity> findById(String id) {
        return Mono.fromCallable(() -> {
                    PasswordRecoveryPO po = mapper.selectById(id);
                    return po == null ? null : toEntity(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Boolean> transition(String id, String fromStatus, String toStatus) {
        return Mono.fromCallable(() -> mapper.update(
                                null,
                                new LambdaUpdateWrapper<PasswordRecoveryPO>()
                                        .eq(PasswordRecoveryPO::getId, id)
                                        .eq(PasswordRecoveryPO::getStatus, fromStatus)
                                        .set(PasswordRecoveryPO::getStatus, toStatus)
                                        .set(PasswordRecoveryPO::getUpdatedAt, LocalDateTime.now()))
                        > 0)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> incrementAttemptCount(String id) {
        return Mono.fromCallable(() -> {
                    mapper.update(
                            null,
                            new LambdaUpdateWrapper<PasswordRecoveryPO>()
                                    .eq(PasswordRecoveryPO::getId, id)
                                    .setSql("attempt_count = attempt_count + 1")
                                    .set(PasswordRecoveryPO::getUpdatedAt, LocalDateTime.now()));
                    PasswordRecoveryPO po = mapper.selectById(id);
                    return po == null || po.getAttemptCount() == null ? 0 : po.getAttemptCount();
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Long> countRecentByContactHash(String contactHash, LocalDateTime since) {
        return Mono.fromCallable(() -> mapper.selectCount(new LambdaQueryWrapper<PasswordRecoveryPO>()
                        .eq(PasswordRecoveryPO::getContactHash, contactHash)
                        .gt(PasswordRecoveryPO::getCreatedAt, since)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Long> countRecentByIpHash(String requestIpHash, LocalDateTime since) {
        return Mono.fromCallable(() -> mapper.selectCount(new LambdaQueryWrapper<PasswordRecoveryPO>()
                        .eq(PasswordRecoveryPO::getRequestIpHash, requestIpHash)
                        .gt(PasswordRecoveryPO::getCreatedAt, since)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    private PasswordRecoveryPO toPO(PasswordRecoveryEntity entity) {
        PasswordRecoveryPO po = new PasswordRecoveryPO();
        po.setId(entity.getId());
        po.setUserId(entity.getUserId());
        po.setTenantId(entity.getTenantId());
        po.setUsername(entity.getUsername());
        po.setChannel(entity.getChannel());
        po.setContactHash(entity.getContactHash());
        po.setStatus(entity.getStatus());
        po.setAttemptCount(entity.getAttemptCount());
        po.setRequestIpHash(entity.getRequestIpHash());
        po.setExpiresAt(entity.getExpiresAt());
        po.setCreatedAt(entity.getCreatedAt());
        po.setUpdatedAt(entity.getUpdatedAt());
        return po;
    }

    private PasswordRecoveryEntity toEntity(PasswordRecoveryPO po) {
        return PasswordRecoveryEntity.builder()
                .id(po.getId())
                .userId(po.getUserId())
                .tenantId(po.getTenantId())
                .username(po.getUsername())
                .channel(po.getChannel())
                .contactHash(po.getContactHash())
                .status(po.getStatus())
                .attemptCount(po.getAttemptCount())
                .requestIpHash(po.getRequestIpHash())
                .expiresAt(po.getExpiresAt())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
