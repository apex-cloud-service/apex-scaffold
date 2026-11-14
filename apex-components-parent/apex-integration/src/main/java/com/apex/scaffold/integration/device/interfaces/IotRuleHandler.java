package com.apex.scaffold.integration.device.interfaces;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.device.application.DeviceRuleManageService;
import com.apex.scaffold.integration.device.interfaces.dto.RuleCreateRequest;
import com.apex.scaffold.integration.device.interfaces.dto.RuleResponse;
import com.apex.scaffold.integration.device.interfaces.dto.RuleToggleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 物联网规则管理 Handler（方案 M5 / 批次 E3）。
 *
 * <p>租户始终取可信上下文；{@code @RequirePermission} 为元数据声明，由宿主安全切面执行。
 * 条件 JSON 的结构化白名单预检由应用层（{@code RuleCondition.parse}）完成。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "集成中心-IoT 规则", description = "设备规则引擎的结构化条件规则管理")
@ConditionalOnProperty(
        prefix = "apex.component.integration.device",
        name = "interfaces-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class IotRuleHandler {

    private final DeviceRuleManageService manageService;

    private final Validator validator;

    /**
     * 新增规则（结构化条件，禁止脚本执行）。
     *
     * @param request 当前请求（JSON body）
     * @return 落库后的规则
     */
    @RequirePermission("iot:rule:manage")
    @Operation(
            operationId = "iotCreateRule",
            summary = "新增规则",
            description = "结构化条件规则（禁止脚本执行），条件 JSON 的结构化白名单预检由应用层完成；需 iot:rule:manage 权限")
    @RequestBody(
            description = "规则创建请求（规则名、条件 JSON、动作类型与配置、启停）",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RuleCreateRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功，data 为落库后的规则",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = RuleResponse.class))),
        @ApiResponse(responseCode = "400", description = "请求体为空、参数校验失败或条件 JSON 非法"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 iot:rule:manage 权限")
    })
    @OperationLog(module = "iot", type = OperationLog.OperationType.INSERT, description = "新增物联网规则")
    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(RuleCreateRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(body -> IotResponses.validate(body, validator))
                .flatMap(body -> IotResponses.currentTenant(request).flatMap(tenantId -> manageService
                        .create(
                                tenantId,
                                body.getRuleName(),
                                body.getConditionJson(),
                                body.getActionType(),
                                body.getActionConfig(),
                                body.getEnabled() == null || body.getEnabled())
                        .map(RuleResponse::from)))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IotResponses::error);
    }

    /**
     * 按 ID 查询规则。
     *
     * @param request 当前请求（path: id）
     * @return 规则详情
     */
    @RequirePermission("iot:rule:read")
    @Operation(operationId = "iotGetRule", summary = "按 ID 查询规则", description = "按规则主键查询详情（租户归属校验）")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "规则主键 ID",
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为规则详情",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = RuleResponse.class))),
        @ApiResponse(responseCode = "404", description = "规则不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 iot:rule:manage 权限")
    })
    public Mono<ServerResponse> get(ServerRequest request) {
        return IotResponses.currentTenant(request)
                .flatMap(tenantId -> manageService
                        .get(tenantId, Long.valueOf(request.pathVariable("id")))
                        .map(RuleResponse::from))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IotResponses::error);
    }

    /**
     * 规则分页列表（E3 管理面补强）。
     *
     * @param request 当前请求（query: keyword/enabled/pageNum/pageSize）
     * @return 分页规则列表
     */
    @RequirePermission("iot:rule:read")
    @Operation(operationId = "iotPageRules", summary = "规则分页列表", description = "按规则名关键字与启停状态分页查询租户规则")
    @Parameters({
        @Parameter(
                name = "keyword",
                in = ParameterIn.QUERY,
                required = false,
                description = "规则名关键字（模糊匹配）",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "enabled",
                in = ParameterIn.QUERY,
                required = false,
                description = "启停过滤（true/false，缺省不过滤）",
                schema = @Schema(type = "boolean")),
        @Parameter(
                name = "pageNum",
                in = ParameterIn.QUERY,
                required = false,
                description = "页码，从 1 开始",
                schema = @Schema(type = "integer")),
        @Parameter(
                name = "pageSize",
                in = ParameterIn.QUERY,
                required = false,
                description = "每页条数（服务端上限 100）",
                schema = @Schema(type = "integer"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为规则分页结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "401", description = "缺少可信租户上下文"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 iot:rule:read 权限")
    })
    public Mono<ServerResponse> page(ServerRequest request) {
        String keyword = request.queryParam("keyword").orElse(null);
        Boolean enabled = request.queryParam("enabled")
                .map(value -> "true".equalsIgnoreCase(value))
                .orElse(null);
        int pageNum = parseIntOrDefault(request.queryParam("pageNum").orElse(null), 1);
        int pageSize = parseIntOrDefault(request.queryParam("pageSize").orElse(null), 20);
        return IotResponses.currentTenant(request)
                .flatMap(tenantId -> manageService.page(tenantId, keyword, enabled, pageNum, pageSize))
                .map(page -> PageResponse.of(
                        page.getList().stream().map(RuleResponse::from).toList(), page.getTotal(), pageNum, pageSize))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IotResponses::error);
    }

    /**
     * 规则启停。
     *
     * @param request 当前请求（path: id，JSON body）
     * @return 完成信号
     */
    @RequirePermission("iot:rule:manage")
    @Operation(
            operationId = "iotToggleRule",
            summary = "规则启停",
            description = "按规则主键启停；enabled 仅支持 true/false（非法值返回 400）")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "规则主键 ID",
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "规则启停请求（enabled: true/false）",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RuleToggleRequest.class)))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "启停完成，data 为空"),
        @ApiResponse(responseCode = "400", description = "请求体为空、参数校验失败或 enabled 非 true/false"),
        @ApiResponse(responseCode = "404", description = "规则不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 iot:rule:manage 权限")
    })
    @OperationLog(module = "iot", type = OperationLog.OperationType.UPDATE, description = "启停物联网规则")
    public Mono<ServerResponse> toggle(ServerRequest request) {
        return request.bodyToMono(RuleToggleRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(body -> IotResponses.validate(body, validator))
                .map(body -> parseEnabled(body.getEnabled()))
                .flatMap(enabled -> IotResponses.currentTenant(request)
                        .flatMap(tenantId ->
                                manageService.toggle(tenantId, Long.valueOf(request.pathVariable("id")), enabled)))
                .then(ServerResponse.ok().bodyValue(Result.success()))
                .onErrorResume(IotResponses::error);
    }

    private boolean parseEnabled(String enabled) {
        if ("true".equalsIgnoreCase(enabled)) {
            return true;
        }
        if ("false".equalsIgnoreCase(enabled)) {
            return false;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "enabled 仅支持 true/false");
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
