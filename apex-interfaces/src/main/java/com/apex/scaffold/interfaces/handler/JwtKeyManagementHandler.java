package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.authcenter.application.service.JwtKeyRotationService;
import com.apex.scaffold.authcenter.domain.model.entity.JwtKeyEntity;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * JWT密钥管理Handler
 *
 * <p>提供JWT密钥轮换、吊销、状态查询等管理接口。
 * 所有操作需要管理员权限。
 *
 * @author Apex Team
 * @since 2026-09-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "JWT密钥管理", description = "JWT签名密钥的轮换、吊销和状态管理")
public class JwtKeyManagementHandler {

    private final JwtKeyRotationService keyRotationService;

    /**
     * 执行 JWT 密钥轮换。
     *
     * <p>生成新的 JWT 签名密钥并将当前密钥置为轮换状态，旧密钥保留 24 小时过渡期用于验证已签发的
     * Token。轮换原因从查询参数 {@code reason} 获取（默认 MANUAL），操作人信息从请求属性中提取。
     *
     * @param request 服务器请求，查询参数 reason 表示轮换原因，请求属性 userId、username 表示操作人
     * @return 密钥轮换成功时返回新密钥版本、生效时间、过期时间及提示信息；失败时返回包含错误信息的错误响应
     */
    @OperationLog(
            module = "JWT密钥管理",
            type = OperationLog.OperationType.UPDATE,
            description = "执行JWT密钥轮换",
            saveRequest = true,
            saveResponse = false)
    @Operation(operationId = "rotateJwtKey", summary = "执行JWT密钥轮换", description = "生成新的JWT签名密钥并将当前密钥设置为轮换状态，支持平滑过渡")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "密钥轮换成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "密钥轮换失败",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> rotateKey(ServerRequest request) {
        String reason = request.queryParam("reason").orElse("MANUAL");
        Long operatorUserId = request.attribute("userId")
                .map(id -> Long.parseLong(id.toString()))
                .orElse(0L);
        String operatorUsername =
                request.attribute("username").map(Object::toString).orElse("UNKNOWN");

        log.info("收到JWT密钥轮换请求: reason={}, operator={}", reason, operatorUsername);

        return keyRotationService
                .rotateKey(reason, operatorUserId, operatorUsername)
                .flatMap(newKey -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("keyVersion", newKey.getKeyVersion());
                    data.put("activatedAt", newKey.getActivatedAt());
                    data.put("expiresAt", newKey.getExpiresAt());
                    data.put("message", "JWT密钥轮换成功，旧密钥将保持24小时轮换期用于验证已签发的Token");

                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.success(data));
                })
                .onErrorResume(e -> {
                    log.error("JWT密钥轮换失败", e);
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.error(500, "密钥轮换失败: " + e.getMessage()));
                });
    }

    /**
     * 紧急吊销指定版本的 JWT 密钥。
     *
     * <p>用于密钥泄露等安全事件，吊销后系统自动触发密钥轮换。密钥版本从路径变量
     * {@code keyVersion} 获取，吊销原因从查询参数 {@code reason} 获取（默认
     * SECURITY_INCIDENT），操作人信息从请求属性中提取。
     *
     * @param request 服务器请求，路径变量 keyVersion 表示待吊销的密钥版本，查询参数 reason 表示吊销原因
     * @return 吊销成功时返回被吊销密钥的版本、吊销时间及提示信息；密钥版本不存在或失败时返回错误响应
     */
    @OperationLog(
            module = "JWT密钥管理",
            type = OperationLog.OperationType.DELETE,
            description = "紧急吊销JWT密钥",
            saveRequest = true,
            saveResponse = false)
    @Operation(operationId = "revokeJwtKey", summary = "紧急吊销JWT密钥", description = "用于密钥泄露等安全事件，立即吊销指定密钥并触发轮换")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "密钥吊销成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "404",
                description = "密钥版本不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> revokeKey(ServerRequest request) {
        String keyVersion = request.pathVariable("keyVersion");
        String reason = request.queryParam("reason").orElse("SECURITY_INCIDENT");
        Long operatorUserId = request.attribute("userId")
                .map(id -> Long.parseLong(id.toString()))
                .orElse(0L);
        String operatorUsername =
                request.attribute("username").map(Object::toString).orElse("UNKNOWN");

        log.warn("收到JWT密钥吊销请求: version={}, reason={}, operator={}", keyVersion, reason, operatorUsername);

        return keyRotationService
                .revokeKey(keyVersion, reason, operatorUserId, operatorUsername)
                .flatMap(revokedKey -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("keyVersion", revokedKey.getKeyVersion());
                    data.put("revokedAt", revokedKey.getRevokedAt());
                    data.put("message", "密钥已紧急吊销，系统已自动触发密钥轮换");

                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.success(data));
                })
                .onErrorResume(e -> {
                    log.error("JWT密钥吊销失败: version={}", keyVersion, e);
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.error(500, "密钥吊销失败: " + e.getMessage()));
                });
    }

    /**
     * 查询 30 天内即将过期的 ACTIVE 状态 JWT 密钥列表，密钥敏感信息脱敏后返回。
     *
     * @param request 服务器请求
     * @return 即将过期密钥信息列表（已脱敏）
     */
    @Operation(operationId = "listExpiringSoonJwtKeys", summary = "查询即将过期的JWT密钥", description = "查询30天内即将过期的ACTIVE密钥列表")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> listExpiringSoonKeys(ServerRequest request) {
        return keyRotationService
                .findExpiringSoonKeys()
                .map(this::maskSensitiveInfo)
                .collectList()
                .flatMap(keys -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(keys)));
    }

    /**
     * 查询当前用于签发 Token 的活跃 JWT 密钥信息（不返回密钥内容，敏感字段脱敏）。
     *
     * @param request 服务器请求
     * @return 当前活跃密钥的版本、过期时间等信息（已脱敏）
     */
    @Operation(
            operationId = "getActiveJwtKey",
            summary = "查询当前活跃的JWT密钥信息",
            description = "返回当前用于签发Token的密钥版本和过期时间（不返回密钥内容）")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> getActiveKey(ServerRequest request) {
        return keyRotationService
                .initializeKeyIfNeeded()
                .map(this::maskSensitiveInfo)
                .flatMap(keyInfo -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(keyInfo)));
    }

    /**
     * 脱敏处理：隐藏密钥内容
     */
    private Map<String, Object> maskSensitiveInfo(JwtKeyEntity key) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", key.getId());
        result.put("keyVersion", key.getKeyVersion());
        result.put("algorithm", key.getAlgorithm());
        result.put("status", key.getStatus());
        result.put("activatedAt", key.getActivatedAt());
        result.put("expiresAt", key.getExpiresAt());
        result.put("deprecatedAt", key.getDeprecatedAt());
        result.put("revokedAt", key.getRevokedAt());
        result.put("rotationReason", key.getRotationReason());
        result.put("operatorUsername", key.getOperatorUsername());
        result.put("createdAt", key.getCreatedAt());
        result.put("isExpiringSoon", key.isExpiringSoon());
        return result;
    }
}
