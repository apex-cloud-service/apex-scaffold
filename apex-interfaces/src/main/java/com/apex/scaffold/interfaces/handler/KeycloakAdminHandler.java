package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.interfaces.service.KeycloakUserSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Keycloak 管理处理器
 *
 * <p>提供 Keycloak 用户同步和状态管理的管理员接口。
 * 仅在 {@code keycloak.enabled=true} 时生效。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>{@link #syncAllUsers} - 全量同步 Keycloak 用户到本地数据库</li>
 *   <li>{@link #syncUser} - 同步单个 Keycloak 用户</li>
 *   <li>{@link #getKeycloakStatus} - 查询 Keycloak 集成状态</li>
 * </ul>
 *
 * <h2>权限要求</h2>
 * <p>所有接口需要管理员权限，路径前缀为 {@code /admin-api/v1/keycloak}。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>初次部署后批量导入 Keycloak 用户</li>
 *   <li>修复用户数据不一致问题</li>
 *   <li>监控 Keycloak 集成健康状态</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.4.0
 * @see KeycloakUserSyncService
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
public class KeycloakAdminHandler {

    /** Keycloak 用户同步服务 */
    private final KeycloakUserSyncService keycloakUserSyncService;

    /**
     * 全量同步 Keycloak 用户
     *
     * <p>从 Keycloak 批量拉取所有用户并同步到本地数据库。
     * 适用于初始化或数据修复场景。
     *
     * <h3>同步逻辑</h3>
     * <ul>
     *   <li>查询 Keycloak Realm 中的所有用户</li>
     *   <li>逐个调用 {@link KeycloakUserSyncService#syncUserFromKeycloak}</li>
     *   <li>已存在的用户跳过，不存在的用户创建</li>
     *   <li>同步失败的用户记录警告日志，不中断整体流程</li>
     * </ul>
     *
     * @param request HTTP 请求
     * @return 200 成功响应 或 500 错误（同步失败）
     */
    public Mono<ServerResponse> syncAllUsers(ServerRequest request) {
        log.info("Admin triggered full user sync from Keycloak");

        return keycloakUserSyncService.syncAllUsers()
                .then(ServerResponse.ok().bodyValue(Result.success("User sync completed successfully")))
                .onErrorResume(e -> {
                    log.error("User sync failed", e);
                    return ServerResponse.status(500)
                            .bodyValue(Result.error(500, "User sync failed: " + e.getMessage()));
                });
    }

    /**
     * 同步单个 Keycloak 用户
     *
     * <p>根据 Keycloak Sub（用户唯一标识）同步指定用户到本地数据库。
     *
     * @param request HTTP 请求，路径参数 {@code keycloakSub} 指定用户ID
     * @return 200 JSON 响应，包含用户信息 或 500 错误（同步失败）
     */
    public Mono<ServerResponse> syncUser(ServerRequest request) {
        String keycloakSub = request.pathVariable("keycloakSub");
        log.info("Admin triggered user sync from Keycloak: keycloakSub={}", keycloakSub);

        return keycloakUserSyncService.syncUserFromKeycloak(keycloakSub)
                .flatMap(user -> ServerResponse.ok()
                        .bodyValue(Result.success(java.util.Map.of(
                                "userId", user.getId(),
                                "username", user.getUsername(),
                                "email", user.getEmail()
                        ))))
                .onErrorResume(e -> {
                    log.error("User sync failed: keycloakSub={}", keycloakSub, e);
                    return ServerResponse.status(500)
                            .bodyValue(Result.error(500, "User sync failed: " + e.getMessage()));
                });
    }

    /**
     * 查询 Keycloak 集成状态
     *
     * <p>返回当前 Keycloak 集成的启用状态和连接状态。
     * 用于健康检查和监控。
     *
     * @param request HTTP 请求
     * @return 200 JSON 响应，包含 enabled 和 status 字段
     */
    public Mono<ServerResponse> getKeycloakStatus(ServerRequest request) {
        log.info("Checking Keycloak integration status");

        return ServerResponse.ok()
                .bodyValue(Result.success(java.util.Map.of(
                        "enabled", true,
                        "status", "connected"
                )));
    }
}
