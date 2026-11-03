package com.apex.scaffold.fileservice.interfaces.handler;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.file.application.service.StorageStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 文件域内部端点 Handler（方案 D5 只读统计）。
 *
 * <p>承载 {@code GET /internal/v1/file/stats}：返回租户已用存储字节数，
 * 契约固定为 {@code Result<{ "storageUsageBytes": N }>，供 system 聚合门面
 * （{@code GET /internal/v1/overview}）消费，字段名勿改。
 *
 * <p>信任模型：{@code /internal/v1/**} 在文件服务为放行路径（集群内服务直连，
 * 不经网关）。租户标识优先取 {@code tenantId} 请求参数（聚合门面显式传递）；
 * 缺失时回退身份中继过滤器建立的可信租户上下文，两者均缺失则以 401 失败。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "内部契约", description = "集群内服务直连接口，不经网关")
public class FileInternalHandler {

    private final StorageStatsService storageStatsService;

    /**
     * 租户存储用量统计（D5 聚合门面消费；契约固定勿改字段名）。
     *
     * @param request 当前请求（可选查询参数 {@code tenantId}）
     * @return {@code Result<{ "storageUsageBytes": N }>}
     */
    @Operation(
            operationId = "internalFileStats",
            summary = "租户存储用量统计（内部）",
            description = "集群内服务直连接口，不经网关。返回租户已用存储字节数，"
                    + "供 system 聚合门面（D5）与能力计量（D4 存储维度）消费；"
                    + "租户标识优先取 tenantId 参数，缺失时回退可信租户上下文，两者均缺失返回 401")
    @Parameter(
            name = "tenantId",
            in = ParameterIn.QUERY,
            required = false,
            description = "租户标识（聚合门面显式传递；缺省回退可信租户上下文）",
            schema = @Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为 {\"storageUsageBytes\": 已用字节数}",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(type = "object", example = "{\"storageUsageBytes\": 2048}"))),
        @ApiResponse(responseCode = "400", description = "参数错误"),
        @ApiResponse(responseCode = "401", description = "缺少可信租户标识"),
        @ApiResponse(responseCode = "500", description = "统计查询失败")
    })
    public Mono<ServerResponse> stats(ServerRequest request) {
        return resolveTenant(request)
                .flatMap(storageStatsService::tenantStorageUsage)
                .flatMap(usageBytes ->
                        ServerResponse.ok().bodyValue(Result.success(Map.of("storageUsageBytes", usageBytes))))
                .onErrorResume(this::toErrorResponse);
    }

    /**
     * 解析租户标识：请求参数优先，缺失时回退可信租户上下文。
     *
     * @param request 当前请求
     * @return 租户标识
     */
    private Mono<String> resolveTenant(ServerRequest request) {
        String tenantId = request.queryParam("tenantId").map(String::trim).orElse(null);
        if (tenantId != null && !tenantId.isBlank()) {
            return Mono.just(tenantId);
        }
        return Mono.deferContextual(context -> {
            String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            if (tenant == null || tenant.isBlank()) {
                return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "缺少可信租户标识"));
            }
            return Mono.just(tenant);
        });
    }

    /**
     * 异常映射为响应：安全类错误映射对应 HTTP 状态，其余业务失败保持 HTTP 200 + Result 错误码。
     *
     * @param error 异常
     * @return 错误响应
     */
    private Mono<ServerResponse> toErrorResponse(Throwable error) {
        if (error instanceof BusinessException businessException) {
            int code = businessException.getCode() == null ? ResultCode.ERROR.getCode() : businessException.getCode();
            HttpStatus status =
                    switch (code) {
                        case 400, 40001 -> HttpStatus.BAD_REQUEST;
                        case 401 -> HttpStatus.UNAUTHORIZED;
                        case 403 -> HttpStatus.FORBIDDEN;
                        default -> HttpStatus.OK;
                    };
            return ServerResponse.status(status).bodyValue(Result.error(code, businessException.getMsg()));
        }
        log.error("文件内部统计端点处理失败", error);
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyValue(Result.error(ResultCode.ERROR.getCode(), ResultCode.ERROR.getMsg()));
    }
}
