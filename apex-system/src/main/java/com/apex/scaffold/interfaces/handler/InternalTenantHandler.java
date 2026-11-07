package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.TenantApplicationService;
import com.apex.scaffold.application.service.TenantPackageService;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.contract.dto.internal.TenantStatusSnapshotDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 租户状态内部查询 Handler（统一认证收口 A6）。
 *
 * <p>承载 {@code GET /internal/v1/tenants/{code}/status}：供认证中心登录前置校验
 * 消费，返回租户存在性、状态机、过期与配额摘要；以及 {@code GET /internal/v1/tenants/{code}/capabilities}：
 * 供网关开放接口能力门禁消费（D4）。集群内服务直连，不经网关；
 * 未知租户以 {@code exists=false} 数据化返回（HTTP 200），便于消费方缓存负结果。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalTenantHandler {

    private final TenantApplicationService tenantApplicationService;

    private final TenantPackageService tenantPackageService;

    /**
     * 查询租户认证期状态。
     *
     * @param request 当前请求（路径参数 {@code code}）
     * @return 状态快照（{@code Result<TenantStatusSnapshotDTO>}，契约类型见 apex-api-contract）
     */
    @Operation(
            operationId = "internalTenantStatus",
            summary = "查询租户认证期状态（内部）",
            description = "集群内服务直连接口，不经网关。返回租户存在性、状态机、过期与配额摘要，"
                    + "供认证中心登录前置校验消费（统一认证收口 A6）；未知租户以 exists=false 数据化返回（HTTP 200）")
    @Parameter(
            name = "code",
            in = ParameterIn.PATH,
            required = true,
            description = "租户编码（长度上限 64）",
            schema = @Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Result 封装的状态快照（未知租户 exists=false）",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TenantStatusSnapshotDTO.class))),
        @ApiResponse(responseCode = "400", description = "code 缺失或超长"),
        @ApiResponse(responseCode = "500", description = "租户状态查询失败")
    })
    public Mono<ServerResponse> getStatus(ServerRequest request) {
        String code = request.pathVariable("code");
        if (code == null || code.isBlank() || code.length() > 64) {
            return ServerResponse.badRequest().bodyValue(Result.error(ResultCode.PARAM_ERROR.getMsg()));
        }
        return tenantApplicationService
                .getTenantStatusByCode(code)
                .flatMap(snapshot -> ServerResponse.ok().bodyValue(Result.success(snapshot)))
                .onErrorResume(error -> {
                    log.error("租户状态内部查询失败: code={}", code, error);
                    return ServerResponse.status(500).bodyValue(Result.error(ResultCode.ERROR.getMsg()));
                });
    }

    /**
     * 查询租户能力订阅快照（D4）。
     *
     * @param request 当前请求（路径参数 {@code code}）
     * @return 能力订阅快照（{@code Result<TenantPackageService.CapabilitySnapshot>}）
     */
    @Operation(
            operationId = "internalTenantCapabilities",
            summary = "查询租户能力订阅快照（内部）",
            description = "集群内服务直连接口，不经网关。返回租户存在性与能力订阅集合"
                    + "（unbounded/空 features = 全量订阅），供网关开放接口能力门禁消费（D4）；"
                    + "未知租户以 exists=false 数据化返回（HTTP 200）")
    @Parameter(
            name = "code",
            in = ParameterIn.PATH,
            required = true,
            description = "租户编码（长度上限 64）",
            schema = @Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Result 封装的能力订阅快照（未知租户 exists=false）",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TenantPackageService.CapabilitySnapshot.class))),
        @ApiResponse(responseCode = "400", description = "code 缺失或超长"),
        @ApiResponse(responseCode = "500", description = "能力订阅查询失败")
    })
    public Mono<ServerResponse> getCapabilities(ServerRequest request) {
        String code = request.pathVariable("code");
        if (code == null || code.isBlank() || code.length() > 64) {
            return ServerResponse.badRequest().bodyValue(Result.error(ResultCode.PARAM_ERROR.getMsg()));
        }
        return tenantPackageService
                .getTenantCapabilities(code)
                .flatMap(snapshot -> ServerResponse.ok().bodyValue(Result.success(snapshot)))
                .onErrorResume(error -> {
                    log.error("租户能力内部查询失败: code={}", code, error);
                    return ServerResponse.status(500).bodyValue(Result.error(ResultCode.ERROR.getMsg()));
                });
    }
}
