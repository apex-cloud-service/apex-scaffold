package com.apex.scaffold.integration.device.interfaces;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.device.application.DeviceQueryService;
import com.apex.scaffold.integration.device.application.DeviceRegistryService;
import com.apex.scaffold.integration.device.interfaces.dto.DeviceRegisterRequest;
import com.apex.scaffold.integration.device.interfaces.dto.DeviceRegisterResponse;
import com.apex.scaffold.integration.device.interfaces.dto.DeviceResponse;
import com.apex.scaffold.integration.device.interfaces.dto.TelemetryResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final Validator validator;

    private final ObjectMapper objectMapper;

    /**
     * 分页查询租户设备。
     *
     * @param request 当前请求（query: status/pageNum/pageSize）
     * @return 分页结果
     */
    @RequirePermission("iot:device:read")
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
