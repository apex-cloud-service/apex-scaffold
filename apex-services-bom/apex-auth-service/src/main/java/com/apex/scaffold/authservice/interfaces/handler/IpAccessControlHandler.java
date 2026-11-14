package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses;
import com.apex.scaffold.auth.domain.model.IpAccessControl;
import com.apex.scaffold.auth.domain.service.IpAccessControlService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.OperationLog.OperationType;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * IP 访问控制管理处理器。
 *
 * <p>租户来自可信请求属性或租户上下文，操作人来自请求属性。列表返回裸 JSON 数组，
 * 其他成功响应使用 Result 包装；服务异步失败由本处理器转换为 HTTP 500。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "IP访问控制", description = "IP白名单/黑名单管理")
public class IpAccessControlHandler {
    private final IpAccessControlService ipAccessControlService;

    /**
     * 添加当前租户的 IP 白名单规则。
     * @param request 查询参数包含 ipAddress 和可选 description 的请求
     * @return Result 包装的规则；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "IP访问控制",
            type = OperationType.INSERT,
            description = "添加IP白名单",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "addToWhitelist", summary = "添加IP白名单", description = "为可信租户添加白名单规则，操作人来自认证请求属性")
    @Parameter(
            name = "ipAddress",
            in = ParameterIn.QUERY,
            description = "IP地址或CIDR段",
            required = true,
            schema = @Schema(implementation = String.class))
    @Parameter(
            name = "description",
            in = ParameterIn.QUERY,
            description = "规则描述",
            schema = @Schema(implementation = String.class))
    @ApiResponse(
            responseCode = "200",
            description = "添加成功",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = IpRuleResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> addToWhitelist(ServerRequest request) {
        String ipAddress = request.queryParam("ipAddress")
                .orElseThrow(() -> new IllegalArgumentException("ipAddress is required"));
        String tenantId = tenantId(request);
        String description = request.queryParam("description").orElse(null);
        String operator = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("operator is required"));
        return ipAccessControlService
                .addToWhitelist(ipAddress, tenantId, description, operator)
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(result)))
                .onErrorResume(this::handleError);
    }

    /**
     * 添加当前租户的 IP 黑名单规则。
     * @param request 含 ipAddress、可选 description 和 durationMinutes 的查询请求
     * @return Result 包装的规则；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "IP访问控制",
            type = OperationType.INSERT,
            description = "添加IP黑名单",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "addToBlacklist",
            summary = "添加IP黑名单",
            description = "为可信租户添加黑名单；不传 durationMinutes 时不设置封禁期限")
    @Parameter(
            name = "ipAddress",
            in = ParameterIn.QUERY,
            description = "IP地址或CIDR段",
            required = true,
            schema = @Schema(implementation = String.class))
    @Parameter(
            name = "description",
            in = ParameterIn.QUERY,
            description = "封禁原因",
            schema = @Schema(implementation = String.class))
    @Parameter(
            name = "durationMinutes",
            in = ParameterIn.QUERY,
            description = "封禁分钟数，不传表示永久",
            schema = @Schema(implementation = Integer.class))
    @ApiResponse(
            responseCode = "200",
            description = "添加成功",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = IpRuleResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> addToBlacklist(ServerRequest request) {
        String ipAddress = request.queryParam("ipAddress")
                .orElseThrow(() -> new IllegalArgumentException("ipAddress is required"));
        String tenantId = tenantId(request);
        String description = request.queryParam("description").orElse(null);
        Integer durationMinutes =
                request.queryParam("durationMinutes").map(Integer::parseInt).orElse(null);
        String operator = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("operator is required"));
        return ipAccessControlService
                .addToBlacklist(ipAddress, tenantId, description, durationMinutes, operator)
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(result)))
                .onErrorResume(this::handleError);
    }

    /**
     * 移除当前租户的白名单地址。
     * @param request 含必填 ipAddress 查询参数的请求
     * @return data 为空的 Result；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "IP访问控制",
            type = OperationType.DELETE,
            description = "移除IP白名单",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "removeFromWhitelist", summary = "移除IP白名单", description = "移除可信租户的指定白名单地址")
    @Parameter(
            name = "ipAddress",
            in = ParameterIn.QUERY,
            description = "要移除的地址",
            required = true,
            schema = @Schema(implementation = String.class))
    @ApiResponse(
            responseCode = "200",
            description = "移除成功",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthCenterApiResponses.EmptyResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> removeFromWhitelist(ServerRequest request) {
        String ipAddress = request.queryParam("ipAddress")
                .orElseThrow(() -> new IllegalArgumentException("ipAddress is required"));
        String tenantId = tenantId(request);
        return ipAccessControlService
                .removeFromWhitelist(ipAddress, tenantId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 移除当前租户的黑名单地址。
     * @param request 含必填 ipAddress 查询参数的请求
     * @return data 为空的 Result；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "IP访问控制",
            type = OperationType.DELETE,
            description = "移除IP黑名单",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "removeFromBlacklist", summary = "移除IP黑名单", description = "移除可信租户的指定黑名单地址")
    @Parameter(
            name = "ipAddress",
            in = ParameterIn.QUERY,
            description = "要解封的地址",
            required = true,
            schema = @Schema(implementation = String.class))
    @ApiResponse(
            responseCode = "200",
            description = "移除成功",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthCenterApiResponses.EmptyResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> removeFromBlacklist(ServerRequest request) {
        String ipAddress = request.queryParam("ipAddress")
                .orElseThrow(() -> new IllegalArgumentException("ipAddress is required"));
        String tenantId = tenantId(request);
        return ipAccessControlService
                .removeFromBlacklist(ipAddress, tenantId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 查询当前租户的白名单。
     * @param request 提供可信租户上下文的请求，无查询参数
     * @return 裸规则数组；服务异步失败返回 HTTP 500 Result
     */
    @OperationLog(
            module = "IP访问控制",
            type = OperationType.SELECT,
            description = "查询IP白名单",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "getWhitelist", summary = "查询IP白名单", description = "查询可信租户的白名单，返回裸 JSON 数组")
    @ApiResponse(
            responseCode = "200",
            description = "规则数组",
            content =
                    @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = IpAccessControl.class))))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> getWhitelist(ServerRequest request) {
        String tenantId = tenantId(request);
        return ipAccessControlService
                .getWhitelist(tenantId)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(list))
                .onErrorResume(this::handleError);
    }

    /**
     * 查询当前租户的黑名单。
     * @param request 提供可信租户上下文的请求，无查询参数
     * @return 裸规则数组；服务异步失败返回 HTTP 500 Result
     */
    @OperationLog(
            module = "IP访问控制",
            type = OperationType.SELECT,
            description = "查询IP黑名单",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "getBlacklist", summary = "查询IP黑名单", description = "查询可信租户的黑名单，返回裸 JSON 数组")
    @ApiResponse(
            responseCode = "200",
            description = "规则数组",
            content =
                    @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = IpAccessControl.class))))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> getBlacklist(ServerRequest request) {
        String tenantId = tenantId(request);
        return ipAccessControlService
                .getBlacklist(tenantId)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(list))
                .onErrorResume(this::handleError);
    }

    /**
     * 启用当前租户的规则。
     * @param request 含 ruleId 路径参数和可信操作人属性的请求
     * @return data 为空的 Result；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "IP访问控制",
            type = OperationType.UPDATE,
            description = "启用IP访问控制规则",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "enableRule", summary = "启用规则", description = "启用可信租户的指定规则，操作人来自请求属性")
    @Parameter(
            name = "ruleId",
            in = ParameterIn.PATH,
            description = "规则ID",
            required = true,
            schema = @Schema(implementation = Long.class))
    @ApiResponse(
            responseCode = "200",
            description = "启用成功",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthCenterApiResponses.EmptyResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> enableRule(ServerRequest request) {
        Long ruleId = Long.parseLong(request.pathVariable("ruleId"));
        String operator = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("operator is required"));
        return ipAccessControlService
                .enableRule(ruleId, tenantId(request), operator)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 禁用当前租户的规则。
     * @param request 含 ruleId 路径参数和可信操作人属性的请求
     * @return data 为空的 Result；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "IP访问控制",
            type = OperationType.UPDATE,
            description = "禁用IP访问控制规则",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "disableRule", summary = "禁用规则", description = "禁用可信租户的指定规则，保留规则记录")
    @Parameter(
            name = "ruleId",
            in = ParameterIn.PATH,
            description = "规则ID",
            required = true,
            schema = @Schema(implementation = Long.class))
    @ApiResponse(
            responseCode = "200",
            description = "禁用成功",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthCenterApiResponses.EmptyResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> disableRule(ServerRequest request) {
        Long ruleId = Long.parseLong(request.pathVariable("ruleId"));
        String operator = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("operator is required"));
        return ipAccessControlService
                .disableRule(ruleId, tenantId(request), operator)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 删除当前租户的规则。
     * @param request 含 ruleId 路径参数和可信操作人属性的请求
     * @return data 为空的 Result；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "IP访问控制",
            type = OperationType.DELETE,
            description = "删除IP访问控制规则",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "deleteRule", summary = "删除规则", description = "删除可信租户的指定规则")
    @Parameter(
            name = "ruleId",
            in = ParameterIn.PATH,
            description = "规则ID",
            required = true,
            schema = @Schema(implementation = Long.class))
    @ApiResponse(
            responseCode = "200",
            description = "删除成功",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthCenterApiResponses.EmptyResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> deleteRule(ServerRequest request) {
        Long ruleId = Long.parseLong(request.pathVariable("ruleId"));
        String operator = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("operator is required"));
        return ipAccessControlService
                .deleteRule(ruleId, tenantId(request), operator)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 调用服务清理过期规则，不额外接收租户查询参数。
     * @param request 当前请求，无业务参数
     * @return Result 包装的 Long 清理数量；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "IP访问控制",
            type = OperationType.DELETE,
            description = "清理过期IP访问控制规则",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "cleanupExpiredRules", summary = "清理过期规则", description = "调用规则服务清理过期记录，返回清理数量")
    @ApiResponse(
            responseCode = "200",
            description = "清理数量",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthCenterApiResponses.CountResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> cleanupExpiredRules(ServerRequest request) {
        return ipAccessControlService
                .cleanupExpiredRules()
                .flatMap(count -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(count)))
                .onErrorResume(this::handleError);
    }

    /**
     * 检查指定 IP 在当前可信租户中的访问权限。
     * @param request 含必填 ipAddress 查询参数的请求
     * @return Result 包装的布尔值；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "IP访问控制",
            type = OperationType.SELECT,
            description = "检查IP访问权限",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "checkAccess", summary = "检查IP访问权限", description = "由规则服务判断指定 IP 在可信租户中是否允许访问")
    @Parameter(
            name = "ipAddress",
            in = ParameterIn.QUERY,
            description = "要检查的IP地址",
            required = true,
            schema = @Schema(implementation = String.class))
    @ApiResponse(
            responseCode = "200",
            description = "是否允许访问",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = IpAccessResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> checkAccess(ServerRequest request) {
        String ipAddress = request.queryParam("ipAddress")
                .orElseThrow(() -> new IllegalArgumentException("ipAddress is required"));
        String tenantId = tenantId(request);
        return ipAccessControlService
                .isAccessAllowed(ipAddress, tenantId)
                .flatMap(allowed -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(allowed)))
                .onErrorResume(this::handleError);
    }

    /**
     * 从请求属性或租户上下文解析可信租户，不读取客户端查询参数。
     * @param request 当前请求
     * @return 非空白租户标识
     */
    private String tenantId(ServerRequest request) {
        String tenantId = request.attribute("tenantId")
                .map(Object::toString)
                .orElseGet(() -> TenantContext.currentTenantId()
                        .orElseThrow(() -> new IllegalArgumentException("tenantId is required")));
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return tenantId;
    }

    /**
     * 将服务异步失败转换为现有 HTTP 500 错误响应。
     * @param error 服务异常
     * @return Result 错误响应
     */
    private Mono<ServerResponse> handleError(Throwable error) {
        log.error("IP访问控制操作失败", error);
        return ServerResponse.status(500)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.error(error.getMessage()));
    }

    /** OpenAPI 专用规则包装。 */
    public static class IpRuleResult extends Result<IpAccessControl> {}

    /** OpenAPI 专用访问判断包装。 */
    public static class IpAccessResult extends Result<Boolean> {}
}
