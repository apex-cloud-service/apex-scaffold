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
import com.apex.scaffold.integration.device.interfaces.dto.MqttAclRequest;
import com.apex.scaffold.integration.device.interfaces.dto.MqttAuthRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@Tag(name = "内部契约-IoT", description = "设备域内部端点与 EMQX 认证/授权钩子")
@ConditionalOnProperty(
        prefix = "apex.component.integration.device",
        name = "interfaces-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class IotInternalHandler {

    private static final int USERNAME_IDENTITY_PARTS = 2;

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
    @Operation(
            operationId = "iotMqttAuth",
            summary = "MQTT 认证钩子（EMQX 调用）",
            description = "EMQX HTTP 认证回调：凭据优先取 tenant/deviceKey/secret，兼容 username={tenantCode}/{deviceKey} "
                    + "复合形态与 password 别名；认证失败（含设备不存在与密码错误）统一 401，避免设备枚举。"
                    + "topic ACL 校验为独立入口（/internal/v1/iot/mqtt/acl），不在认证链内强制绑定")
    @RequestBody(
            description = "MQTT 认证请求（tenant/deviceKey/secret 或 username/password 别名）",
            required = true,
            content =
                    @Content(mediaType = "application/json", schema = @Schema(implementation = MqttAuthRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "认证通过，data 为 {\"authenticated\": true}",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(type = "object", example = "{\"authenticated\": true}"))),
        @ApiResponse(responseCode = "401", description = "认证拒绝（请求体缺失/设备不存在/凭据错误）")
    })
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
     * MQTT 授权钩子（EMQX HTTP Authorize 口径，方案 M5 / 批次 E3 ACL 收口）。
     *
     * <p>EMQX 在设备 pub/sub 时调用：按 {@code username = {tenantCode}/{deviceKey}} 解析身份，
     * 用 {@link DeviceRegistryService#authorizeTopic} 判定 topic 是否落在本租户本设备命名空间。
     * 返回 EMQX 规范的 {@code {"result": "allow"|"deny"}}——这是 <b>broker 层强制隔离</b>的关键，
     * 仅靠应用层策略无法阻止越权订阅。
     *
     * <p>解析失败、设备身份缺失或 topic 越权一律返回 {@code deny}（fail-closed）。
     *
     * @param request 当前请求（JSON body：username / topic / action）
     * @return {@code {"result":"allow"}} 或 {@code {"result":"deny"}}
     */
    @Operation(
            operationId = "iotMqttAcl",
            summary = "MQTT 授权钩子（EMQX 调用）",
            description = "EMQX HTTP Authorize 回调：按 username={tenantCode}/{deviceKey} 解析身份，判定 topic 是否落在"
                    + "本租户本设备命名空间。返回 EMQX 规范的 {\"result\":\"allow\"|\"deny\"}，是 broker 层强制隔离的关键；"
                    + "解析失败、身份缺失或 topic 越权一律 deny（fail-closed）")
    @RequestBody(
            description = "MQTT 授权请求（username/topic/action）",
            required = false,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MqttAclRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "授权判定结果，data 为 {\"result\": \"allow\"} 或 {\"result\": \"deny\"}",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(type = "object", example = "{\"result\": \"allow\"}")))
    })
    public Mono<ServerResponse> mqttAcl(ServerRequest request) {
        return request.bodyToMono(MqttAclRequest.class)
                .defaultIfEmpty(new MqttAclRequest())
                .map(acl -> {
                    String[] identity = parseIdentity(acl.getUsername());
                    boolean allowed = identity != null
                            && registryService.authorizeTopic(identity[0], identity[1], acl.getTopic());
                    return Map.of("result", allowed ? "allow" : "deny");
                })
                .onErrorReturn(Map.of("result", "deny"))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    /**
     * 解析 EMQX username 为 {@code [tenantCode, deviceKey]}。
     *
     * @param username EMQX username（{@code {tenantCode}/{deviceKey}}）
     * @return 解析结果；非法时为 null
     */
    private String[] parseIdentity(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        String[] parts = username.split("/", USERNAME_IDENTITY_PARTS);
        if (parts.length != USERNAME_IDENTITY_PARTS || parts[0].isBlank() || parts[1].isBlank()) {
            return null;
        }
        return new String[] {parts[0].trim(), parts[1].trim()};
    }

    /**
     * 下发设备命令（幂等：重复 commandId 返回既有命令）。
     *
     * @param request 当前请求（path: key 设备标识，JSON body）
     * @return 命令受理结果
     */
    @Operation(
            operationId = "iotDispatchDeviceCommand",
            summary = "下发设备命令（内部）",
            description =
                    "集群内服务直连接口。幂等 commandId：重复提交返回既有命令并标记 duplicate=true，不重复发布；" + "命令载荷序列化为 JSON 后经 MQTT 下行；租户取可信上下文")
    @Parameter(
            name = "key",
            in = ParameterIn.PATH,
            required = true,
            description = "设备标识（device_key）",
            schema = @Schema(type = "string"))
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
                description = "命令受理结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CommandResponse.class))),
        @ApiResponse(responseCode = "400", description = "请求体为空或参数校验失败"),
        @ApiResponse(responseCode = "401", description = "缺少可信租户上下文")
    })
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
     * 设备统计（D5 聚合门面与 D4 计量消费；契约固定勿改字段名）。
     *
     * <p>租户标识优先取 {@code tenantId} 请求参数（system 计量/聚合门面显式传递，
     * 与 file 域 {@code /internal/v1/file/stats} 口径一致）；缺失时回退可信租户上下文。
     *
     * @param request 当前请求（可选查询参数 {@code tenantId}）
     * @return {@code Result<{ "deviceCount": N }>}
     */
    @Operation(
            operationId = "iotDeviceStats",
            summary = "设备统计（内部）",
            description = "集群内服务直连接口，不经网关。返回租户设备总数，供 system 聚合门面（D5）与能力计量"
                    + "（D4 设备维度）消费；租户标识优先取 tenantId 参数（显式传递），缺失时回退可信租户上下文")
    @Parameter(
            name = "tenantId",
            in = ParameterIn.QUERY,
            required = false,
            description = "租户标识（计量/聚合门面显式传递；缺省回退可信租户上下文）",
            schema = @Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为 {\"deviceCount\": 设备总数}",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(
                                                implementation = DeviceStatsResponse.class,
                                                example = "{\"deviceCount\": 36}"))),
        @ApiResponse(responseCode = "401", description = "缺少可信租户标识"),
        @ApiResponse(responseCode = "500", description = "统计查询失败")
    })
    public Mono<ServerResponse> stats(ServerRequest request) {
        return resolveTenant(request)
                .flatMap(queryService::deviceCount)
                .map(DeviceStatsResponse::new)
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IotResponses::error);
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
        return IotResponses.currentTenant(request);
    }

    private record Credential(String tenantId, String deviceKey, String secret) {}

    /** 解析认证凭据：tenant/deviceKey 缺失时从 username 复合形态补齐。 */
    private Credential resolveCredential(MqttAuthRequest body) {
        String tenant = trimToNull(body.getTenant());
        String deviceKey = trimToNull(body.getDeviceKey());
        String username = trimToNull(body.getUsername());
        if (needsUsernameIdentity(tenant, deviceKey, username)) {
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

    private boolean needsUsernameIdentity(String tenant, String deviceKey, String username) {
        return (tenant == null || deviceKey == null) && username != null;
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
