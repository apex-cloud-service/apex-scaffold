package com.apex.scaffold.integration.device.interfaces;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.device.application.DeviceCommandService;
import com.apex.scaffold.integration.device.application.DeviceQueryService;
import com.apex.scaffold.integration.device.application.DeviceRegistryService;
import com.apex.scaffold.integration.device.domain.repository.DeviceCommandRepository;
import com.apex.scaffold.integration.device.domain.repository.DeviceShadowRepository;
import com.apex.scaffold.integration.device.interfaces.dto.CommandDispatchRequest;
import com.apex.scaffold.integration.device.interfaces.dto.CommandResponse;
import com.apex.scaffold.integration.device.interfaces.dto.DeviceRegisterRequest;
import com.apex.scaffold.integration.device.interfaces.dto.DeviceRegisterResponse;
import com.apex.scaffold.integration.device.interfaces.dto.DeviceResponse;
import com.apex.scaffold.integration.device.interfaces.dto.DeviceShadowResponse;
import com.apex.scaffold.integration.device.interfaces.dto.TelemetryResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 设备管理 Handler（方案 M5 / 批次 E3，C7 标准管理接口）。
 *
 * <p>租户始终取可信上下文；{@code @RequirePermission} 为元数据声明，由宿主安全切面执行。
 * 设备注册响应含一次性明文 secret，其余查询响应均不含任何凭据（secretHash 不透出）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "集成中心-IoT 设备管理", description = "设备注册、注销、遥测、影子与命令管理")
@ConditionalOnProperty(
        prefix = "apex.component.integration.device",
        name = "interfaces-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DeviceAdminHandler {

    private static final String PAGE_NUM_DEFAULT = "1";

    private static final String PAGE_SIZE_DEFAULT = "20";

    private final DeviceQueryService queryService;

    private final DeviceRegistryService registryService;

    private final DeviceCommandService commandService;

    private final DeviceShadowRepository shadowRepository;

    private final DeviceCommandRepository commandRepository;

    private final Validator validator;

    private final ObjectMapper objectMapper;

    /**
     * 分页查询租户设备。
     *
     * @param request 当前请求（query: status/pageNum/pageSize）
     * @return 分页结果
     */
    @RequirePermission("iot:device:read")
    @Operation(
            operationId = "iotListDevices",
            summary = "分页查询租户设备",
            description = "按可信租户上下文分页查询设备，可按状态过滤；需 iot:device:read 权限")
    @Parameters({
        @Parameter(
                name = "status",
                in = ParameterIn.QUERY,
                required = false,
                description = "设备状态过滤（如 ACTIVE/INACTIVE）",
                schema = @Schema(type = "string")),
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
                description = "查询成功，data 为设备分页结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "401", description = "缺少可信租户上下文"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 iot:device:read 权限")
    })
    public Mono<ServerResponse> page(ServerRequest request) {
        String status = request.queryParam("status").orElse(null);
        int pageNum = parseIntOrDefault(request.queryParam("pageNum").orElse(PAGE_NUM_DEFAULT), 1);
        int pageSize = parseIntOrDefault(request.queryParam("pageSize").orElse(PAGE_SIZE_DEFAULT), 20);
        return IotResponses.currentTenant(request)
                .flatMap(tenantId -> queryService.page(tenantId, status, pageNum, pageSize))
                .map(page -> PageResponse.of(
                        page.getList().stream().map(DeviceResponse::from).toList(), page.getTotal(), pageNum, pageSize))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IotResponses::error);
    }

    /**
     * 按 ID 查询设备。
     *
     * @param request 当前请求（path: id）
     * @return 设备详情（不含凭据）
     */
    @RequirePermission("iot:device:read")
    @Operation(operationId = "iotGetDevice", summary = "按 ID 查询设备", description = "按设备主键查询详情（先校验租户归属，杜绝跨租户读取）；响应不含任何凭据")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "设备主键 ID",
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为设备详情（不含凭据）",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = DeviceResponse.class))),
        @ApiResponse(responseCode = "401", description = "缺少可信租户上下文"),
        @ApiResponse(responseCode = "404", description = "设备不存在或不属于当前租户"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 iot:device:read 权限")
    })
    public Mono<ServerResponse> get(ServerRequest request) {
        return IotResponses.currentTenant(request)
                .flatMap(tenantId -> queryService
                        .get(tenantId, Long.valueOf(request.pathVariable("id")))
                        .map(DeviceResponse::from))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IotResponses::error);
    }

    /**
     * 注册设备（服务端生成 secret，明文仅在本响应出现一次）。
     *
     * @param request 当前请求（JSON body）
     * @return 注册结果（含一次性 secret）
     */
    @RequirePermission("iot:device:manage")
    @Operation(
            operationId = "iotRegisterDevice",
            summary = "注册设备",
            description = "服务端生成设备 secret，明文仅在本响应出现一次（服务端只存摘要）；需 iot:device:manage 权限")
    @RequestBody(
            description = "设备注册请求（设备标识、名称、标签等）",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DeviceRegisterRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "注册成功，data 含一次性明文 secret",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = DeviceRegisterResponse.class))),
        @ApiResponse(responseCode = "400", description = "请求体为空或参数校验失败"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 iot:device:manage 权限")
    })
    @OperationLog(module = "iot", type = OperationLog.OperationType.INSERT, description = "注册物联网设备")
    public Mono<ServerResponse> register(ServerRequest request) {
        return request.bodyToMono(DeviceRegisterRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(body -> IotResponses.validate(body, validator))
                .flatMap(body -> IotResponses.currentTenant(request).flatMap(tenantId -> registryService
                        .register(
                                tenantId,
                                body.getDeviceKey(),
                                body.getCertFingerprint(),
                                body.getFirmwareVersion(),
                                serializeLabels(body.getLabels()))
                        .map(DeviceRegisterResponse::from)))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IotResponses::error);
    }

    /**
     * 注销设备（逻辑删除）。
     *
     * @param request 当前请求（path: id）
     * @return 完成信号
     */
    @RequirePermission("iot:device:manage")
    @Operation(
            operationId = "iotDeregisterDevice",
            summary = "注销设备",
            description = "按设备主键注销（逻辑删除），先校验租户归属；需 iot:device:manage 权限")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "设备主键 ID",
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "注销成功，data 为空"),
        @ApiResponse(responseCode = "401", description = "缺少可信租户上下文"),
        @ApiResponse(responseCode = "404", description = "设备不存在或不属于当前租户"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 iot:device:manage 权限")
    })
    @OperationLog(module = "iot", type = OperationLog.OperationType.DELETE, description = "注销物联网设备")
    public Mono<ServerResponse> deregister(ServerRequest request) {
        return IotResponses.currentTenant(request)
                .flatMap(tenantId -> registryService.deregister(tenantId, Long.valueOf(request.pathVariable("id"))))
                .then(ServerResponse.ok().bodyValue(Result.success()))
                .onErrorResume(IotResponses::error);
    }

    /**
     * 查询设备遥测（先校验设备归属，杜绝跨租户读取）。
     *
     * @param request 当前请求（path: id，query: pageNum/pageSize）
     * @return 遥测分页
     */
    @RequirePermission("iot:telemetry:read")
    @Operation(
            operationId = "iotListDeviceTelemetry",
            summary = "查询设备遥测",
            description = "先校验设备归属再做遥测分页查询（杜绝跨租户读取）；需 iot:telemetry:read 权限")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "设备主键 ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameters({
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
                description = "查询成功，data 为遥测分页结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "401", description = "缺少可信租户上下文"),
        @ApiResponse(responseCode = "404", description = "设备不存在或不属于当前租户"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 iot:telemetry:read 权限")
    })
    public Mono<ServerResponse> telemetry(ServerRequest request) {
        int pageNum = parseIntOrDefault(request.queryParam("pageNum").orElse(PAGE_NUM_DEFAULT), 1);
        int pageSize = parseIntOrDefault(request.queryParam("pageSize").orElse(PAGE_SIZE_DEFAULT), 20);
        return IotResponses.currentTenant(request)
                .flatMap(tenantId ->
                        queryService.telemetry(tenantId, Long.valueOf(request.pathVariable("id")), pageNum, pageSize))
                .map(page -> PageResponse.of(
                        page.getList().stream().map(TelemetryResponse::from).toList(),
                        page.getTotal(),
                        pageNum,
                        pageSize))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IotResponses::error);
    }

    /**
     * 查询设备影子（desired/reported + version 乐观锁，E3 管理面补强）。
     *
     * <p>先按设备主键校验归属，再按 {@code device_key} 读取影子；不存在的影子按 404 返回。
     *
     * @param request 当前请求（path: id）
     * @return 影子详情
     */
    @RequirePermission("iot:device:read")
    @Operation(
            operationId = "iotGetDeviceShadow",
            summary = "查询设备影子",
            description = "先按设备主键校验归属，再按 device_key 读取影子（desired/reported + version 乐观锁）；" + "影子不存在返回 404")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "设备主键 ID",
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为影子详情（desired/reported 解析为 JSON 节点）",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = DeviceShadowResponse.class))),
        @ApiResponse(responseCode = "401", description = "缺少可信租户上下文"),
        @ApiResponse(responseCode = "404", description = "设备或影子不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 iot:device:read 权限")
    })
    public Mono<ServerResponse> shadow(ServerRequest request) {
        return IotResponses.currentTenant(request)
                .flatMap(tenantId -> queryService
                        .get(tenantId, Long.valueOf(request.pathVariable("id")))
                        .flatMap(device -> shadowRepository
                                .findByKey(tenantId, device.getDeviceKey())
                                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "设备影子不存在")))
                                .map(shadow -> DeviceShadowResponse.from(shadow, objectMapper))))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IotResponses::error);
    }

    /**
     * 管理端命令下发（幂等 commandId，E3 管理面补强）。
     *
     * <p>设备存在性校验与下行发布由应用层完成；重复 commandId 返回既有命令并标记
     * {@code duplicate=true}，不重复发布。
     *
     * @param request 当前请求（path: id，JSON body: commandId/payload/timeoutSeconds）
     * @return 命令状态
     */
    @RequirePermission("iot:command:send")
    @Operation(
            operationId = "iotDispatchDeviceCommandAdmin",
            summary = "管理端命令下发",
            description = "幂等 commandId：重复提交返回既有命令并标记 duplicate=true，不重复发布；" + "设备存在性校验与下行发布由应用层完成")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "设备主键 ID",
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "命令下发请求（commandId/payload/timeoutSeconds）",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CommandDispatchRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "命令状态（duplicate=true 表示命中幂等）",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CommandResponse.class))),
        @ApiResponse(responseCode = "400", description = "请求体为空或参数校验失败"),
        @ApiResponse(responseCode = "401", description = "缺少可信租户上下文"),
        @ApiResponse(responseCode = "404", description = "设备不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 iot:command:send 权限")
    })
    @OperationLog(module = "iot", type = OperationLog.OperationType.INSERT, description = "下发物联网设备命令")
    public Mono<ServerResponse> dispatchCommand(ServerRequest request) {
        return request.bodyToMono(CommandDispatchRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(body -> IotResponses.validate(body, validator))
                .flatMap(body -> IotResponses.currentTenant(request).flatMap(tenantId -> queryService
                        .get(tenantId, Long.valueOf(request.pathVariable("id")))
                        .flatMap(device -> commandService.dispatch(
                                tenantId,
                                device.getDeviceKey(),
                                body.getCommandId(),
                                serializePayload(body.getPayload()),
                                body.getTimeoutSeconds()))))
                .map(result -> CommandResponse.from(result.command(), result.duplicate()))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IotResponses::error);
    }

    /**
     * 查询设备命令历史（E3 管理面补强）。
     *
     * @param request 当前请求（path: id，query: pageNum/pageSize）
     * @return 命令分页
     */
    @RequirePermission("iot:device:read")
    @Operation(
            operationId = "iotListDeviceCommands",
            summary = "查询设备命令历史",
            description = "先校验设备归属，再按设备分页查询命令历史（按创建时间倒序）")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "设备主键 ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameters({
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
                description = "查询成功，data 为命令分页结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "401", description = "缺少可信租户上下文"),
        @ApiResponse(responseCode = "404", description = "设备不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 iot:device:read 权限")
    })
    public Mono<ServerResponse> commands(ServerRequest request) {
        int pageNum = parseIntOrDefault(request.queryParam("pageNum").orElse(PAGE_NUM_DEFAULT), 1);
        int pageSize = parseIntOrDefault(request.queryParam("pageSize").orElse(PAGE_SIZE_DEFAULT), 20);
        return IotResponses.currentTenant(request)
                .flatMap(tenantId -> queryService
                        .get(tenantId, Long.valueOf(request.pathVariable("id")))
                        .flatMap(device ->
                                commandRepository.pageByDevice(tenantId, device.getDeviceKey(), pageNum, pageSize)))
                .map(page -> PageResponse.of(
                        page.getList().stream()
                                .map(command -> CommandResponse.from(command, false))
                                .toList(),
                        page.getTotal(),
                        pageNum,
                        pageSize))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IotResponses::error);
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "命令载荷序列化失败");
        }
    }

    private String serializeLabels(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(labels);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "设备标签序列化失败");
        }
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
