package com.apex.scaffold.integration.device.interfaces;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.device.application.DeviceCommandService;
import com.apex.scaffold.integration.device.application.DeviceQueryService;
import com.apex.scaffold.integration.device.application.DeviceRegistryService;
import com.apex.scaffold.integration.device.interfaces.dto.CommandDispatchRequest;
import com.apex.scaffold.integration.device.interfaces.dto.CommandResponse;
import com.apex.scaffold.integration.device.interfaces.dto.DeviceStatsResponse;
import com.apex.scaffold.integration.device.interfaces.dto.MqttAuthRequest;
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
 * 设备域内部端点 Handler（方案 M5 / 批次 E3）。
 *
 * <p>服务间/设备面内部端点，不暴露到管理前端：
 * <ul>
 *   <li>POST {@code /internal/v1/iot/mqtt/auth}：MQTT 认证钩子（EMQX 调用；
 *       无租户上下文，凭据自请求体解析；口径为认证通过 200、失败 401）；</li>
 *   <li>POST {@code /internal/v1/iot/devices/{key}/commands}：命令下行（幂等 commandId，
 *       租户取可信上下文）；</li>
 *   <li>GET {@code /internal/v1/iot/devices/stats}：设备统计（D5 聚合门面消费，
 *       契约固定 {@code Result<{ "deviceCount": N }>})。</li>
 * </ul>
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
public class IotInternalHandler {

    private final DeviceRegistryService registryService;

    private final DeviceCommandService commandService;

    private final DeviceQueryService queryService;

    private final Validator validator;

    private final ObjectMapper objectMapper;

    /**
     * MQTT 认证钩子（EMQX HTTP 认证口径）。
     *
     * <p>凭据优先取 {@code tenant/deviceKey/secret}，兼容 EMQX 常见的
     * {@code username = {tenantCode}/{deviceKey}} 复合形态与 {@code password} 别名；
     * 认证失败（含设备不存在与密码错误）统一 401，避免设备枚举。
     * topic ACL 校验为独立可复用入口
     * （{@link DeviceRegistryService#authorizeTopic}），不在认证链内强制绑定。
     *
     * @param request 当前请求（JSON body）
     * @return 200（认证通过）或 401（拒绝）
     */
    public Mono<ServerResponse> mqttAuth(ServerRequest request) {
        return request.bodyToMono(MqttAuthRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "认证请求体缺失")))
                .map(this::resolveCredential)
                .flatMap(credential -> registryService.authenticate(
                        credential.tenantId(), credential.deviceKey(), credential.secret()))
                .flatMap(device -> ServerResponse.ok().bodyValue(Result.success(Map.of("authenticated", true))))
                .onErrorResume(IotResponses::error);
    }

    /**
     * 下发设备命令（幂等：重复 commandId 返回既有命令）。
     *
     * @param request 当前请求（path: key 设备标识，JSON body）
     * @return 命令受理结果
     */
    @OperationLog(module = "iot", type = OperationLog.OperationType.INSERT, description = "下发设备命令")
    public Mono<ServerResponse> dispatchCommand(ServerRequest request) {
        String deviceKey = request.pathVariable("key");
        return request.bodyToMono(CommandDispatchRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(body -> IotResponses.validate(body, validator))
                .flatMap(body -> IotResponses.currentTenant(request).flatMap(tenantId -> commandService
                        .dispatch(
                                tenantId,
                                deviceKey,
                                body.getCommandId(),
                                serializePayload(body.getPayload()),
                                body.getTimeoutSeconds())
                        .map(result -> CommandResponse.from(result.command(), result.duplicate()))))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IotResponses::error);
    }

    /**
     * 设备统计（D5 聚合门面消费；契约固定勿改字段名）。
     *
     * @param request 当前请求
     * @return {@code Result<{ "deviceCount": N }>}
     */
    public Mono<ServerResponse> stats(ServerRequest request) {
        return IotResponses.currentTenant(request)
                .flatMap(queryService::deviceCount)
                .map(DeviceStatsResponse::new)
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IotResponses::error);
    }

    private record Credential(String tenantId, String deviceKey, String secret) {}

    /** 解析认证凭据：tenant/deviceKey 缺失时从 username 复合形态补齐。 */
    private Credential resolveCredential(MqttAuthRequest body) {
        String tenant = trimToNull(body.getTenant());
        String deviceKey = trimToNull(body.getDeviceKey());
        String username = trimToNull(body.getUsername());
        if ((tenant == null || deviceKey == null) && username != null) {
            int slash = username.indexOf('/');
            if (slash > 0) {
                if (tenant == null) {
                    tenant = username.substring(0, slash);
                }
                if (deviceKey == null) {
                    deviceKey = username.substring(slash + 1);
                }
            } else if (deviceKey == null) {
                deviceKey = username;
            }
        }
        String secret = body.getSecret() != null && !body.getSecret().isBlank() ? body.getSecret() : body.getPassword();
        return new Credential(tenant, deviceKey, secret);
    }

    private String serializePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "命令载荷序列化失败");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
