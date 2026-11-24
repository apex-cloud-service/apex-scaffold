package com.apex.scaffold.interfaces.service;

import com.apex.scaffold.application.service.TenantGuard;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.UserRepository;
import com.apex.scaffold.interfaces.config.KeycloakConfig;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Keycloak 用户同步服务
 *
 * <p>提供从 Keycloak 批量或单个同步用户到本地数据库的功能。
 * 仅在 {@code keycloak.enabled=true} 时生效。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>{@link #syncUserFromKeycloak} - 从 Keycloak 同步单个用户</li>
 *   <li>{@link #syncAllUsers} - 从 Keycloak 批量同步所有用户</li>
 *   <li>{@code createUserInKeycloak} - 在 Keycloak 中创建新用户（预留）</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>使用 Keycloak Sub（用户唯一标识）作为关联键</li>
 *   <li>本地已存在的用户跳过同步（避免覆盖本地修改）</li>
 *   <li>新用户自动创建并关联 Keycloak Sub</li>
 *   <li>同步失败的用户记录警告日志，不中断整体流程</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>核心设计原则：
 * <ul>
 *   <li>Keycloak Admin Client 是阻塞式 API，必须使用 {@link BlockingIoSchedulers#remote()} 隔离</li>
 *   <li>使用 {@code Mono.fromCallable()} 包装阻塞调用</li>
 *   <li>使用 {@code subscribeOn()} 指定调度器</li>
 *   <li>使用 {@code flatMap()} 进行响应式链式转换</li>
 *   <li>严禁使用 {@code .block()} 阻塞主线程</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>系统初始化：批量导入 Keycloak 现有用户</li>
 *   <li>数据修复：修复本地与 Keycloak 用户数据不一致</li>
 *   <li>增量同步：OIDC 登录时自动同步新用户</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.4.0
 * @see Keycloak
 * @see UserRepresentation
 * @see BlockingIoSchedulers
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
public class KeycloakUserSyncService {

    private static final String STATUS_SYNCED = "SYNCED";
    private static final String STATUS_SKIPPED = "SKIPPED";

    private static final int HTTP_STATUS_CREATED = 201;

    /** Keycloak Admin Client（阻塞式 API） */
    private final Keycloak keycloakAdminClient;

    /** Keycloak 配置 */
    private final KeycloakConfig keycloakConfig;

    /** 用户仓储 */
    private final UserRepository userRepository;

    /** 租户写链路守卫：镜像建档前校验租户状态与用户配额 */
    private final TenantGuard tenantGuard;

    /**
     * 从 Keycloak 同步单个用户
     *
     * <p>根据 Keycloak Sub（用户唯一标识）从 Keycloak 获取用户信息，
     * 并同步到本地数据库。如果本地已存在该用户，则跳过创建。
     *
     * <p><strong>处理流程</strong></p>
     * <ol>
     *   <li>调用 Keycloak Admin API 获取用户信息（阻塞调用，隔离到 {@code remote} 调度器）</li>
     *   <li>检查本地数据库是否已存在该用户（根据 keycloak_sub）</li>
     *   <li>如果不存在，创建新用户并保存</li>
     *   <li>返回用户实体</li>
     * </ol>
     *
     * <p><strong>字段映射</strong></p>
     * <ul>
     *   <li>username ← kcUser.username 或 kcUser.email（如果 username 为空）</li>
     *   <li>email ← kcUser.email</li>
     *   <li>keycloakSub ← kcUser.id</li>
     *   <li>nickName ← kcUser.firstName 或 kcUser.username</li>
     *   <li>password ← ""（空密码，使用 OIDC 认证）</li>
     *   <li>status ← 1（启用）</li>
     *   <li>deleted ← 0（未删除）</li>
     * </ul>
     *
     * <p><strong>错误处理</strong></p>
     * <ul>
     *   <li>Keycloak API 调用失败 → 记录错误日志并返回 Mono.error</li>
     *   <li>数据库保存失败 → 由调用方处理异常</li>
     * </ul>
     *
     * @param keycloakSub Keycloak 用户唯一标识（Sub）
     * @return {@link Mono<UserEntity>} 同步后的用户实体
     */
    public Mono<UserEntity> syncUserFromKeycloak(String keycloakSub) {
        if (keycloakSub == null || keycloakSub.isBlank()) {
            return Mono.error(new IllegalArgumentException("keycloakSub 不能为空"));
        }
        return userRepository
                .findByKeycloakSub(keycloakSub)
                .switchIfEmpty(Mono.defer(() -> fetchAndCreate(keycloakSub)));
    }

    /** 拉取 Keycloak 用户并在本地建档；已存在判断由调用方先行完成。 */
    private Mono<UserEntity> fetchAndCreate(String keycloakSub) {
        return Mono.fromCallable(() -> keycloakAdminClient
                        .realm(keycloakConfig.getRealm())
                        .users()
                        .get(keycloakSub)
                        .toRepresentation())
                .subscribeOn(BlockingIoSchedulers.remote())
                .flatMap(kcUser -> {
                    String username = kcUser.getUsername();
                    String email = kcUser.getEmail();
                    String firstName = kcUser.getFirstName();
                    UserEntity user = new UserEntity();
                    user.setUsername(username != null ? username : email);
                    user.setEmail(email);
                    user.setKeycloakSub(keycloakSub);
                    user.setNickName(firstName != null ? firstName : username);
                    user.setPassword("");
                    user.setStatus(1);
                    user.setDeleted(0);
                    user.setCreateTime(LocalDateTime.now());
                    user.setUpdateTime(LocalDateTime.now());
                    return tenantGuard
                            .assertUserQuota(1)
                            .then(userRepository.save(user))
                            .onErrorResume(error -> userRepository
                                    .findByKeycloakSub(keycloakSub)
                                    .switchIfEmpty(Mono.error(error)))
                            .doOnSuccess(savedUser -> log.info(
                                    "User synced from Keycloak: userId={}, username={}, keycloakSub={}",
                                    savedUser.getId(),
                                    savedUser.getUsername(),
                                    keycloakSub));
                })
                .onErrorMap(
                        error -> !(error instanceof com.apex.scaffold.common.exception.BusinessException),
                        error -> new RuntimeException(
                                "Failed to sync user from Keycloak: " + error.getMessage(), error));
    }

    /**
     * 从 Keycloak 全量同步用户，返回逐项统计结果，失败不再伪装为成功。
     *
     * @return 同步统计
     */
    public Mono<SyncResult> syncAllUsers() {
        return Mono.fromCallable(() -> keycloakAdminClient
                        .realm(keycloakConfig.getRealm())
                        .users()
                        .list())
                .subscribeOn(BlockingIoSchedulers.remote())
                .flatMapMany(reactor.core.publisher.Flux::fromIterable)
                .concatMap(kcUser -> {
                    String keycloakSub = kcUser.getId();
                    if (keycloakSub == null || keycloakSub.isBlank()) {
                        return Mono.just(SyncOutcome.failed("", "missing keycloak sub"));
                    }
                    return userRepository
                            .findByKeycloakSub(keycloakSub)
                            .map(existing -> SyncOutcome.skipped(keycloakSub))
                            .switchIfEmpty(syncUserFromKeycloak(keycloakSub)
                                    .map(saved -> SyncOutcome.synced(keycloakSub))
                                    .onErrorResume(error -> {
                                        log.warn(
                                                "Failed to sync user: keycloakSub={}, error={}",
                                                keycloakSub,
                                                error.getMessage());
                                        return Mono.just(SyncOutcome.failed(keycloakSub, error.getMessage()));
                                    }));
                })
                .collectList()
                .map(SyncResult::from)
                .doOnNext(result -> log.info(
                        "Full user sync finished: requested={}, synced={}, skipped={}, failed={}",
                        result.requested(),
                        result.synced(),
                        result.skipped(),
                        result.failed()));
    }

    /** 单用户同步结果。 */
    private record SyncOutcome(String keycloakSub, String status, String error) {
        private static SyncOutcome synced(String sub) {
            return new SyncOutcome(sub, "SYNCED", null);
        }

        private static SyncOutcome skipped(String sub) {
            return new SyncOutcome(sub, "SKIPPED", null);
        }

        private static SyncOutcome failed(String sub, String error) {
            return new SyncOutcome(sub, "FAILED", error);
        }
    }

    /** 全量同步统计结果。 */
    public record SyncResult(int requested, int synced, int skipped, int failed, java.util.List<String> errors) {
        private static SyncResult from(java.util.List<SyncOutcome> outcomes) {
            int synced = 0;
            int skipped = 0;
            int failed = 0;
            java.util.List<String> errors = new java.util.ArrayList<>();
            for (SyncOutcome outcome : outcomes) {
                if (STATUS_SYNCED.equals(outcome.status())) {
                    synced++;
                } else if (STATUS_SKIPPED.equals(outcome.status())) {
                    skipped++;
                } else {
                    failed++;
                    errors.add(outcome.keycloakSub() + ": " + outcome.error());
                }
            }
            return new SyncResult(outcomes.size(), synced, skipped, failed, java.util.List.copyOf(errors));
        }
    }

    /**
     * 探测 Keycloak 真实连通性。
     *
     * @return 连通状态
     */
    public Mono<StatusResult> status() {
        return Mono.fromCallable(() -> {
                    long started = System.currentTimeMillis();
                    keycloakAdminClient.realm(keycloakConfig.getRealm()).toRepresentation();
                    return new StatusResult(
                            true, "CONNECTED", keycloakConfig.getRealm(), null, System.currentTimeMillis() - started);
                })
                .subscribeOn(BlockingIoSchedulers.remote())
                .onErrorResume(error -> {
                    log.warn("Keycloak status check failed: {}", error.getMessage());
                    return Mono.just(
                            new StatusResult(true, "ERROR", keycloakConfig.getRealm(), error.getMessage(), -1L));
                });
    }

    /** Keycloak 连通状态。 */
    public record StatusResult(boolean enabled, String status, String realm, String error, long latencyMs) {}

    /**
     * 在 Keycloak 中创建用户并回写本地数据库。
     *
     * <p>先调用 Keycloak Admin API 创建用户（创建失败时发出错误信号），可选地重置初始密码，
     * 然后将返回的 Keycloak Sub 写入用户实体并保存到本地数据库。</p>
     *
     * @param user 待创建的用户实体，需包含用户名等基本信息
     * @param password 初始密码，为空时跳过密码设置
     * @return {@link Mono<UserEntity>} 已保存并关联 Keycloak Sub 的用户实体
     */
    public Mono<UserEntity> createKeycloakUser(UserEntity user, String password) {
        return Mono.fromCallable(() -> {
                    log.info("Creating user in Keycloak: username={}", user.getUsername());

                    UserRepresentation kcUser = new UserRepresentation();
                    kcUser.setUsername(user.getUsername());
                    kcUser.setEmail(user.getEmail());
                    kcUser.setFirstName(user.getNickName());
                    kcUser.setEnabled(true);
                    kcUser.setEmailVerified(false);

                    jakarta.ws.rs.core.Response response = keycloakAdminClient
                            .realm(keycloakConfig.getRealm())
                            .users()
                            .create(kcUser);

                    if (response.getStatus() != HTTP_STATUS_CREATED) {
                        throw new RuntimeException("Failed to create user in Keycloak: status=" + response.getStatus());
                    }

                    String locationHeader = response.getHeaderString("Location");
                    String keycloakSub = locationHeader.substring(locationHeader.lastIndexOf('/') + 1);

                    if (password != null && !password.isEmpty()) {
                        org.keycloak.representations.idm.CredentialRepresentation credential =
                                new org.keycloak.representations.idm.CredentialRepresentation();
                        credential.setType(org.keycloak.representations.idm.CredentialRepresentation.PASSWORD);
                        credential.setValue(password);
                        credential.setTemporary(false);

                        keycloakAdminClient
                                .realm(keycloakConfig.getRealm())
                                .users()
                                .get(keycloakSub)
                                .resetPassword(credential);
                    }

                    log.info("User created in Keycloak: keycloakSub={}, username={}", keycloakSub, user.getUsername());

                    return keycloakSub;
                })
                .subscribeOn(BlockingIoSchedulers.remote())
                .flatMap(keycloakSub -> {
                    user.setKeycloakSub(keycloakSub);
                    return tenantGuard
                            .assertUserQuota(1)
                            .then(userRepository
                                    .save(user)
                                    .doOnSuccess(savedUser -> log.info(
                                            "User saved with Keycloak reference: userId={}, keycloakSub={}",
                                            savedUser.getId(),
                                            keycloakSub)));
                })
                .onErrorResume(e -> {
                    log.error("Failed to create user in Keycloak: username={}", user.getUsername(), e);
                    return Mono.error(new RuntimeException("Failed to create user in Keycloak: " + e.getMessage(), e));
                });
    }
}
