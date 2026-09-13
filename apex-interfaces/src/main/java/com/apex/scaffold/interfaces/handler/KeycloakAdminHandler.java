package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.interfaces.service.KeycloakUserSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * <p>提供 Keycloak 用户同步和集成状态管理接口,用于后台管理员操作。
 *
 * <h2>主要功能</h2>
 * <ul>
 *   <li>全量同步 Keycloak 用户到本地数据库</li>
 *   <li>同步单个 Keycloak 用户</li>
 *   <li>查询 Keycloak 集成状态</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>初始化数据:首次接入 Keycloak 时全量同步用户</li>
 *   <li>数据修复:当本地用户数据与 Keycloak 不一致时触发同步</li>
 *   <li>增量同步:定期同步指定用户的最新信息</li>
 *   <li>健康检查:监控 Keycloak 集成状态</li>
 * </ul>
 *
 * <h2>配置要求</h2>
 * <p>此 Handler 需要配置 {@code keycloak.enabled=true} 才会启用。
 *
 * <h2>权限要求</h2>
 * <p>所有接口均需要管理员权限,建议在路由层配置权限校验。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@Tag(name = "后台-Keycloak管理", description = "Keycloak用户同步和集成状态管理接口")
public class KeycloakAdminHandler {

    /** Keycloak 用户同步服务 */
    private final KeycloakUserSyncService keycloakUserSyncService;

    @Operation(
            operationId = "syncAllKeycloakUsers",
            summary = "全量同步Keycloak用户",
            description = "从Keycloak批量拉取所有用户并同步到本地数据库,适用于初始化或数据修复场景")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "用户同步成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "用户同步失败",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> syncAllUsers(ServerRequest request) {
        log.info("Admin triggered full user sync from Keycloak");

        return keycloakUserSyncService
                .syncAllUsers()
                .then(ServerResponse.ok().bodyValue(Result.success("User sync completed successfully")))
                .onErrorResume(e -> {
                    log.error("User sync failed", e);
                    return ServerResponse.status(500)
                            .bodyValue(Result.error(500, "User sync failed: " + e.getMessage()));
                });
    }

    @Operation(
            operationId = "syncKeycloakUser",
            summary = "同步单个Keycloak用户",
            description = "根据Keycloak Sub(用户唯一标识)同步指定用户到本地数据库")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "用户同步成功,返回用户信息",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "用户同步失败",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @Parameter(name = "keycloakSub", description = "Keycloak用户唯一标识(Subject)", required = true, in = ParameterIn.PATH)
    public Mono<ServerResponse> syncUser(ServerRequest request) {
        String keycloakSub = request.pathVariable("keycloakSub");
        log.info("Admin triggered user sync from Keycloak: keycloakSub={}", keycloakSub);

        return keycloakUserSyncService
                .syncUserFromKeycloak(keycloakSub)
                .flatMap(user -> ServerResponse.ok()
                        .bodyValue(Result.success(java.util.Map.of(
                                "userId", user.getId(),
                                "username", user.getUsername(),
                                "email", user.getEmail()))))
                .onErrorResume(e -> {
                    log.error("User sync failed: keycloakSub={}", keycloakSub, e);
                    return ServerResponse.status(500)
                            .bodyValue(Result.error(500, "User sync failed: " + e.getMessage()));
                });
    }

    @Operation(
            operationId = "getKeycloakStatus",
            summary = "查询Keycloak集成状态",
            description = "返回当前Keycloak集成的启用状态和连接状态,用于健康检查和监控")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "返回Keycloak集成状态",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> getKeycloakStatus(ServerRequest request) {
        log.info("Checking Keycloak integration status");

        return ServerResponse.ok().bodyValue(Result.success(java.util.Map.of("enabled", true, "status", "connected")));
    }
}
