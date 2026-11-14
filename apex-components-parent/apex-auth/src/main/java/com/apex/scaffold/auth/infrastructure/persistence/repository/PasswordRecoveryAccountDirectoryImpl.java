package com.apex.scaffold.auth.infrastructure.persistence.repository;

import com.apex.scaffold.auth.domain.repository.PasswordRecoveryAccountDirectory;
import com.apex.scaffold.auth.infrastructure.persistence.mapper.UserMapper;
import com.apex.scaffold.auth.infrastructure.persistence.po.UserPO;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 密码找回账号目录实现（服务端可信入口）。
 *
 * <p>按已验证联系方式跨租户解析账号：恢复流程由匿名用户发起，目标租户在解析前未知，
 * 因此查询不经过租户作用域包装；结果仅区分唯一命中 / 无命中 / 多命中，多命中不暴露账号明细。
 * 密码重设以显式 userId + tenantId 条件执行，客户端无法选择或影响目标租户。
 * 同步 JDBC 统一隔离到 jdbc 池。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class PasswordRecoveryAccountDirectoryImpl implements PasswordRecoveryAccountDirectory {

    /** 账号状态：正常。 */
    private static final int STATUS_ACTIVE = 1;

    /** 逻辑删除标记：未删除。 */
    private static final int NOT_DELETED = 0;

    private final UserMapper userMapper;

    @Override
    public Mono<Resolution> resolveByEmail(String email) {
        return resolve(UserPO::getEmail, email);
    }

    @Override
    public Mono<Resolution> resolveByPhone(String phone) {
        return resolve(UserPO::getPhone, phone);
    }

    @Override
    public Mono<Boolean> updatePasswordHash(Long userId, String tenantId, String passwordHash) {
        return Mono.fromCallable(() -> userMapper.update(
                                null,
                                new LambdaUpdateWrapper<UserPO>()
                                        .eq(UserPO::getId, userId)
                                        .eq(UserPO::getTenantId, tenantId)
                                        .eq(UserPO::getStatus, STATUS_ACTIVE)
                                        .eq(UserPO::getDeleted, NOT_DELETED)
                                        .set(UserPO::getPasswordHash, passwordHash)
                                        .set(UserPO::getUpdatedAt, LocalDateTime.now()))
                        == 1)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    private Mono<Resolution> resolve(
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<UserPO, ?> column, String value) {
        return Mono.fromCallable(() -> {
                    List<UserPO> matches = userMapper.selectList(new LambdaQueryWrapper<UserPO>()
                            .eq(column, value)
                            .eq(UserPO::getStatus, STATUS_ACTIVE)
                            .eq(UserPO::getDeleted, NOT_DELETED));
                    if (matches.isEmpty()) {
                        return Resolution.none();
                    }
                    if (matches.size() > 1) {
                        return Resolution.multiple();
                    }
                    UserPO po = matches.get(0);
                    return Resolution.unique(po.getId(), po.getTenantId(), po.getUsername());
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
