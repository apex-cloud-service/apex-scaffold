package com.apex.scaffold.officeservice.interfaces.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.office.application.OfficeApplicationService;
import com.apex.scaffold.office.application.dto.LoginResponse;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.infrastructure.spi.OfficeOAuthClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 办公平台公开接口处理器
 *
 * <p>提供办公平台 OAuth 认证的公开接口，包括平台状态查询、授权 URL 生成和 OAuth 回调处理。
 *
 * <p>接口特点：
 * <ul>
 *   <li>无需认证：所有接口均为公开接口，不需要 JWT 认证</li>
 *   <li>租户隔离：通过配置的 loginTenantId 确定租户范围</li>
 *   <li>安全防护：state 防重放、平台 token 不返回浏览器、多重校验链</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Component
@RequiredArgsConstructor
@Tag(name = "办公平台", description = "企业办公平台登录、状态和回调接口")
public class OfficeHandler {
    private final OfficeApplicationService service;
    private final List<OfficeOAuthClient> clients;
    private final com.apex.scaffold.office.infrastructure.config.OfficePlatformProperties properties;

    /** 已注册平台列表响应。 */
    @Schema(name = "OfficePlatformStatusResult")
    public static class PlatformStatusResult extends Result<List<String>> {}

    /** 授权地址响应。 */
    @Schema(name = "OfficeAuthorizationUrlResult")
    public static class AuthorizationUrlResult extends Result<String> {}

    /** 办公平台登录响应。 */
    @Schema(name = "OfficeLoginResult")
    public static class LoginResult extends Result<LoginResponse> {}

    /**
     * 查询已注册办公平台状态
     *
     * <p>返回当前服务已注册的办公平台列表，用于前端判断可用的登录方式。
     *
     * @param request HTTP 请求
     * @return 平台列表响应
     */
    @Operation(
            operationId = "listOfficePlatformStatus",
            summary = "查询平台状态",
            description = "返回当前服务已注册的办公平台及启用状态，用于前端动态显示可用的登录方式",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "查询成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PlatformStatusResult.class)))
            })
    @OperationLog(
            module = "Office",
            type = OperationLog.OperationType.SELECT,
            description = "status",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> status(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.success(
                        clients.stream().map(client -> client.platform().name()).toList()));
    }

    /**
     * 生成办公平台授权地址
     *
     * <p>生成绑定租户的 state 并返回平台授权地址，用户点击后跳转到平台授权页。
     *
     * @param request HTTP 请求，查询参数包含 platform
     * @return 授权 URL 响应
     */
    @Operation(
            operationId = "startOfficeLogin",
            summary = "开始办公登录",
            description = "生成服务端 state 并返回办公平台授权地址，state 有效期 5 分钟",
            parameters = {
                @Parameter(
                        name = "platform",
                        description = "平台类型",
                        required = true,
                        example = "FEISHU",
                        schema = @Schema(allowableValues = {"FEISHU", "DINGTALK", "WECOM"}))
            },
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "生成成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = AuthorizationUrlResult.class))),
                @ApiResponse(responseCode = "400", description = "平台参数无效"),
                @ApiResponse(responseCode = "500", description = "租户未配置应用或应用未启用")
            })
    @OperationLog(
            module = "Office",
            type = OperationLog.OperationType.OTHER,
            description = "start",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> start(ServerRequest request) {
        return tenantScoped(platform(request).flatMap(service::authorizationUrl))
                .flatMap(url -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(url)));
    }

    /**
     * 处理办公平台 OAuth 回调
     *
     * <p>接收平台回调的授权码和 state，完成以下步骤：
     * <ol>
     *   <li>校验 code 和 state 参数非空</li>
     *   <li>原子消费 state 并校验租户、平台和回调地址</li>
     *   <li>使用授权码兑换平台 access token</li>
     *   <li>获取外部用户标识</li>
     *   <li>查询已审批的绑定关系</li>
     *   <li>校验 Apex 用户状态</li>
     *   <li>签发 Apex access token 和 refresh token</li>
     * </ol>
     *
     * <p>安全要点：
     * <ul>
     *   <li>平台 access token 不返回给浏览器</li>
     *   <li>只有 APPROVED 状态的绑定才能登录</li>
     *   <li>任一环节失败均返回登录失败，不泄露具体原因</li>
     * </ul>
     *
     * @param request HTTP 请求，查询参数包含 platform、code、state
     * @return 登录响应，包含 Apex JWT
     */
    @Operation(
            operationId = "officeLoginCallback",
            summary = "办公登录回调",
            description = "接收平台授权码和 state，完成认证并签发 Apex JWT。授权码只在服务端兑换，平台 access token 不返回浏览器",
            parameters = {
                @Parameter(
                        name = "platform",
                        description = "平台类型",
                        required = true,
                        example = "FEISHU",
                        schema = @Schema(allowableValues = {"FEISHU", "DINGTALK", "WECOM"})),
                @Parameter(name = "code", description = "平台授权码（一次性有效）", required = true, example = "abc123"),
                @Parameter(name = "state", description = "服务端签发的状态值", required = true, example = "xyz789")
            },
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "登录成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = LoginResult.class))),
                @ApiResponse(responseCode = "400", description = "参数无效或 state 已失效"),
                @ApiResponse(responseCode = "500", description = "认证失败或绑定未审批")
            })
    @OperationLog(
            module = "Office",
            type = OperationLog.OperationType.OTHER,
            description = "callback",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> callback(ServerRequest request) {
        String code = request.queryParam("code").orElse("");
        String state = request.queryParam("state").orElse("");
        if (code.isBlank() || state.isBlank()) {
            return ServerResponse.badRequest()
                    .bodyValue(Result.error(com.apex.scaffold.common.result.ResultCode.PARAM_ERROR));
        }
        String clientIp =
                request.remoteAddress().map(address -> address.getHostString()).orElse("");
        return tenantScoped(platform(request)
                        .flatMap(platform -> service.login(
                                platform, code, state, request.headers().firstHeader("X-Device-Id"), clientIp)))
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(response)));
    }

    /**
     * 在租户上下文中执行操作
     *
     * <p>从配置中获取登录租户 ID 并设置到响应式上下文中。
     *
     * @param operation 待执行的响应式操作
     * @param <T> 操作结果类型
     * @return 租户上下文中的操作结果
     */
    private <T> Mono<T> tenantScoped(Mono<T> operation) {
        String loginTenantId = properties.getLoginTenantId();
        if (!properties.isEnabled() || loginTenantId == null || loginTenantId.isBlank()) {
            return Mono.error(new com.apex.scaffold.common.exception.BusinessException(
                    com.apex.scaffold.common.result.ResultCode.LOGIN_FAILED));
        }
        return com.apex.scaffold.common.context.TenantContext.withTenant(operation)
                .contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant(loginTenantId));
    }

    /**
     * 从请求中解析平台类型
     *
     * @param request HTTP 请求
     * @return 平台类型
     */
    private Mono<OfficePlatform> platform(ServerRequest request) {
        return Mono.justOrEmpty(request.queryParam("platform"))
                .map(value -> value.toUpperCase(java.util.Locale.ROOT))
                .flatMap(value -> {
                    try {
                        return Mono.just(OfficePlatform.valueOf(value));
                    } catch (IllegalArgumentException error) {
                        return Mono.error(new com.apex.scaffold.common.exception.BusinessException(
                                com.apex.scaffold.common.result.ResultCode.PARAM_ERROR));
                    }
                })
                .switchIfEmpty(Mono.error(new com.apex.scaffold.common.exception.BusinessException(
                        com.apex.scaffold.common.result.ResultCode.PARAM_ERROR)));
    }
}
