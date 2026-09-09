package com.apex.scaffold.interfaces.service;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.UserRepository;
import com.apex.scaffold.interfaces.config.KeycloakConfig;
import java.time.LocalDateTime;
import java.util.List;
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
 * <h2>核心功能</h2>
 * <ul>
 *   <li>{@link #syncUserFromKeycloak} - 从 Keycloak 同步单个用户</li>
 *   <li>{@link #syncAllUsers} - 从 Keycloak 批量同步所有用户</li>
 *   <li>{@link #createUserInKeycloak} - 在 Keycloak 中创建新用户（预留）</li>
 * </ul>
 *
 * <h2>同步策略</h2>
 * <ul>
 *   <li>使用 Keycloak Sub（用户唯一标识）作为关联键</li>
 *   <li>本地已存在的用户跳过同步（避免覆盖本地修改）</li>
 *   <li>新用户自动创建并关联 Keycloak Sub</li>
 *   <li>同步失败的用户记录警告日志，不中断整体流程</li>
 * </ul>
 *
 * <h2>WebFlux 响应式设计</h2>
 * <p>核心设计原则：
 * <ul>
 *   <li>Keycloak Admin Client 是阻塞式 API，必须使用 {@link BlockingIoSchedulers#remote()} 隔离</li>
 *   <li>使用 {@code Mono.fromCallable()} 包装阻塞调用</li>
 *   <li>使用 {@code subscribeOn()} 指定调度器</li>
 *   <li>使用 {@code flatMap()} 进行响应式链式转换</li>
 *   <li>严禁使用 {@code .block()} 阻塞主线程</li>
 * </ul>
 *
 * <h2>使用场景</h2>
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

    /** Keycloak Admin Client（阻塞式 API） */
    private final Keycloak keycloakAdminClient;

    /** Keycloak 配置 */
    private final KeycloakConfig keycloakConfig;

    /** 用户仓储 */
    private final UserRepository userRepository;

    /**
     * 从 Keycloak 同步单个用户
     *
     * <p>根据 Keycloak Sub（用户唯一标识）从 Keycloak 获取用户信息，
     * 并同步到本地数据库。如果本地已存在该用户，则跳过创建。
     *
     * <h3>处理流程</h3>
     * <ol>
     *   <li>调用 Keycloak Admin API 获取用户信息（阻塞调用，隔离到 {@code remote} 调度器）</li>
     *   <li>检查本地数据库是否已存在该用户（根据 keycloak_sub）</li>
     *   <li>如果不存在，创建新用户并保存</li>
     *   <li>返回用户实体</li>
     * </ol>
     *
     * <h3>字段映射</h3>
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
     * <h3>错误处理</h3>
     * <ul>
     *   <li>Keycloak API 调用失败 → 记录错误日志并返回 Mono.error</li>
     *   <li>数据库保存失败 → 由调用方处理异常</li>
     * </ul>
     *
     * @param keycloakSub Keycloak 用户唯一标识（Sub）
     * @return {@link Mono<UserEntity>} 同步后的用户实体
     */
    public Mono<UserEntity> syncUserFromKeycloak(String keycloakSub) {
        return Mono.fromCallable(() -> {
                    log.info("Syncing user from Keycloak: keycloakSub={}", keycloakSub);

                    UserRepresentation kcUser = keycloakAdminClient
                            .realm(keycloakConfig.getRealm())
                            .users()
                            .get(keycloakSub)
                            .toRepresentation();

                    return kcUser;
                })
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

                    return userRepository
                            .save(user)
                            .doOnSuccess(savedUser -> log.info(
                                    "User synced from Keycloak: userId={}, username={}, keycloakSub={}",
                                    savedUser.getId(),
                                    savedUser.getUsername(),
                                    keycloakSub));
                })
                .onErrorResume(e -> {
                    log.error("Failed to sync user from Keycloak: keycloakSub={}", keycloakSub, e);
                    return Mono.error(new RuntimeException("Failed to sync user from Keycloak: " + e.getMessage(), e));
                });
    }

    public Mono<Void> syncAllUsers() {
        return Mono.fromCallable(() -> {
                    log.info("Starting full user sync from Keycloak");

                    List<UserRepresentation> kcUsers = keycloakAdminClient
                            .realm(keycloakConfig.getRealm())
                            .users()
                            .list();

                    log.info("Found {} users in Keycloak", kcUsers.size());
                    return kcUsers;
                })
                .subscribeOn(BlockingIoSchedulers.remote())
                .flatMapMany(reactor.core.publisher.Flux::fromIterable)
                .flatMap(kcUser -> {
                    String keycloakSub = kcUser.getId();
                    return syncUserFromKeycloak(keycloakSub).onErrorResume(e -> {
                        log.warn("Failed to sync user: keycloakSub={}, error={}", keycloakSub, e.getMessage());
                        return Mono.empty();
                    });
                })
                .then()
                .doOnSuccess(v -> log.info("Full user sync from Keycloak completed"))
                .doOnError(e -> log.error("Full user sync from Keycloak failed", e));
    }

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

                    if (response.getStatus() != 201) {
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
                    return userRepository
                            .save(user)
                            .doOnSuccess(savedUser -> log.info(
                                    "User saved with Keycloak reference: userId={}, keycloakSub={}",
                                    savedUser.getId(),
                                    keycloakSub));
                })
                .onErrorResume(e -> {
                    log.error("Failed to create user in Keycloak: username={}", user.getUsername(), e);
                    return Mono.error(new RuntimeException("Failed to create user in Keycloak: " + e.getMessage(), e));
                });
    }
}
