package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.SysSsoProviderService;
import com.apex.scaffold.application.service.SysSsoSessionService;
import com.apex.scaffold.application.support.sso.SsoProtocolAdapterFactory;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * SSO 认证端点处理器(函数式端点)
 *
 * <p>提供 SSO 单点登录的认证端点,包括发起认证、处理回调和登出功能。
 * 作为 SSO 客户端(SP)与 SSO 提供商(IdP)之间的桥梁,实现标准的 SSO 认证流程。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>发起认证:生成认证请求并重定向到 SSO 提供商</li>
 *   <li>处理回调:接收 SSO 提供商的认证响应,验证用户身份并建立本地会话</li>
 *   <li>SSO 登出:注销 SSO 会话,并可选择性地重定向到 SSO 提供商的登出端点</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ol>
 *   <li>用户访问 /auth/sso/initiate 发起认证,指定 SSO 提供商 ID</li>
 *   <li>系统生成认证请求,重定向用户到 SSO 提供商的登录页面</li>
 *   <li>用户在 SSO 提供商完成身份验证</li>
 *   <li>SSO 提供商重定向回 /auth/sso/callback,携带认证响应</li>
 *   <li>系统验证认证响应,提取用户信息,建立本地会话</li>
 *   <li>返回 SSO 会话标识、用户信息和会话过期时间</li>
 * </ol>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>GET /auth/sso/initiate - 发起 SSO 认证(公开端点)</li>
 *   <li>POST /auth/sso/callback - SSO 回调处理(公开端点)</li>
 *   <li>POST /auth/sso/logout - SSO 登出(需认证)</li>
 * </ul>
 *
 * @author Apex Scaffold Generator
 * @since 1.3.1
 */
@Component
@Tag(name = "SSO 认证端点", description = "SSO 单点登录认证端点")
public class SsoAuthHandler extends BaseHandler {

    private final SysSsoProviderService sysSsoProviderService;
    private final SysSsoSessionService sysSsoSessionService;
    private final SsoProtocolAdapterFactory ssoProtocolAdapterFactory;

    /**
     * 创建 SSO 认证端点处理器。
     *
     * @param sysSsoProviderService SSO 提供商服务
     * @param sysSsoSessionService SSO 会话服务
     * @param ssoProtocolAdapterFactory SSO 协议适配器工厂
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public SsoAuthHandler(
            SysSsoProviderService sysSsoProviderService,
            SysSsoSessionService sysSsoSessionService,
            SsoProtocolAdapterFactory ssoProtocolAdapterFactory,
            MessageUtils messageUtils,
            Validator validator) {
        super(messageUtils, validator);
        this.sysSsoProviderService = sysSsoProviderService;
        this.sysSsoSessionService = sysSsoSessionService;
        this.ssoProtocolAdapterFactory = ssoProtocolAdapterFactory;
    }

    /**
     * 发起 SSO 认证
     *
     * <p>根据指定的 SSO 提供商 ID,生成认证请求并返回重定向 URL。
     * 前端接收到重定向 URL 后,应将用户重定向到 SSO 提供商的登录页面。
     *
     * <p>认证流程:
     * <ol>
     *   <li>查询 SSO 提供商配置</li>
     *   <li>根据协议类型获取协议适配器</li>
     *   <li>生成认证请求(包含 CallbackURL)</li>
     *   <li>返回重定向 URL</li>
     * </ol>
     *
     * @param request HTTP 请求,查询参数 providerId 指定 SSO 提供商 ID
     * @return 带 traceId 的统一成功响应,data 为 {"redirectUrl": "SSO 提供商登录 URL"}
     */
    @Operation(operationId = "initiateSsoAuth", summary = "发起 SSO 认证", description = "根据指定的 SSO 提供商,生成认证请求并返回重定向 URL")
    @Parameter(
            name = "providerId",
            description = "SSO 提供商 ID",
            required = true,
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", format = "int64", example = "1"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "生成认证请求成功,data 为协议相关数据,多数协议含 redirectUrl 重定向地址"),
        @ApiResponse(responseCode = "400", description = "参数错误,提供商 ID 必填且为正整数"),
        @ApiResponse(responseCode = "404", description = "SSO 提供商不存在或已禁用")
    })
    @OperationLog(
            module = "SsoAuth",
            type = OperationLog.OperationType.INSERT,
            description = "initiate",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> initiate(ServerRequest request) {
        Long providerId = request.queryParam("providerId").map(Long::parseLong).orElse(null);
        if (providerId == null || providerId <= 0) {
            return buildError(400, "INVALID_PARAMETER", "error.sso.provider_id_required");
        }

        String callbackUrl = buildCallbackUrl(request);

        return sysSsoProviderService
                .getById(providerId)
                .flatMap(provider -> {
                    var adapter = ssoProtocolAdapterFactory.getAdapter(provider.getProtocolType());
                    Map<String, Object> config = sysSsoProviderService.buildProviderConfig(provider);
                    return adapter.initiate(config, callbackUrl);
                })
                .flatMap(this::buildSuccess);
    }

    /**
     * SSO 回调处理
     *
     * <p>接收 SSO 提供商的认证响应,验证用户身份,提取用户信息,建立本地会话。
     * 根据协议类型(SAML/CAS/OIDC 等)解析不同格式的认证响应。
     *
     * <p>回调流程:
     * <ol>
     *   <li>提取回调参数(如 SAMLResponse、ticket、code 等)</li>
     *   <li>根据协议类型获取协议适配器</li>
     *   <li>验证认证响应,提取用户信息</li>
     *   <li>查找或创建本地用户</li>
     *   <li>创建 SSO 会话记录</li>
     *   <li>生成 SSO-TOKEN-{sessionId} 格式的会话标识</li>
     *   <li>返回认证结果</li>
     * </ol>
     *
     * @param request HTTP 请求,包含 SSO 提供商返回的认证响应参数
     * @return 带 traceId 的统一成功响应，data 包含 sessionId、token（SSO 会话标识，非 JWT）、userInfo 和 expiryTime
     */
    @Operation(operationId = "handleSsoCallback", summary = "SSO 回调处理", description = "接收 SSO 提供商的认证响应,验证用户身份并建立本地会话")
    @Parameter(
            name = "providerId",
            description = "SSO 提供商 ID",
            required = true,
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "认证成功，返回sessionId、SSO会话标识token（非JWT）、userInfo和expiryTime"),
        @ApiResponse(responseCode = "400", description = "认证响应参数错误或验证失败"),
        @ApiResponse(responseCode = "401", description = "认证失败,用户身份无效"),
        @ApiResponse(responseCode = "404", description = "SSO 提供商不存在或已禁用")
    })
    @OperationLog(
            module = "统一认证管理",
            type = OperationLog.OperationType.LOGIN,
            description = "SSO回调处理",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> callback(ServerRequest request) {
        Long providerId = request.queryParam("providerId").map(Long::parseLong).orElse(null);
        if (providerId == null || providerId <= 0) {
            return buildError(400, "INVALID_PARAMETER", "error.sso.provider_id_required");
        }

        Map<String, String> callbackParams = request.queryParams().toSingleValueMap().entrySet().stream()
                .filter(entry -> !"providerId".equals(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return sysSsoProviderService
                .getById(providerId)
                .flatMap(provider -> {
                    var adapter = ssoProtocolAdapterFactory.getAdapter(provider.getProtocolType());
                    Map<String, Object> config = sysSsoProviderService.buildProviderConfig(provider);
                    return adapter.authenticate(config, callbackParams)
                            .flatMap(userInfo ->
                                    sysSsoSessionService.handleSuccessfulAuth(provider.getId(), userInfo, request));
                })
                .flatMap(this::buildSuccess);
    }

    /**
     * SSO 登出
     *
     * <p>注销当前用户的 SSO 会话,撤销本地会话并可选择性地重定向到 SSO 提供商的登出端点。
     * 实现单点登出(SLO),确保用户在所有关联系统中都被登出。
     *
     * <p>登出流程:
     * <ol>
     *   <li>获取当前用户的 SSO 会话信息</li>
     *   <li>撤销本地会话,使 JWT 令牌失效</li>
     *   <li>根据协议类型调用 SSO 提供商的登出端点(可选)</li>
     *   <li>返回登出结果和重定向 URL(如有)</li>
     * </ol>
     *
     * @param request HTTP 请求,需携带有效的认证令牌
     * @return 带 traceId 的统一成功响应,data 为 {"success": true, "logoutUrl": "SSO 提供商登出 URL(可选)"}
     */
    @Operation(
            operationId = "ssoLogout",
            summary = "SSO 登出",
            description = "注销当前用户的 SSO 会话,撤销本地会话并可选择性地重定向到 SSO 提供商的登出端点")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "登出成功,data 含 success、revokedCount、message"),
        @ApiResponse(responseCode = "401", description = "未授权,需要先登录"),
        @ApiResponse(responseCode = "404", description = "SSO 会话不存在")
    })
    @OperationLog(
            module = "SsoAuth",
            type = OperationLog.OperationType.LOGOUT,
            description = "logout",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> logout(ServerRequest request) {
        return sysSsoSessionService.handleLogout(request).flatMap(this::buildSuccess);
    }

    /**
     * 构建 SSO 回调 URL
     *
     * <p>根据当前请求构建完整的回调 URL,用于 SSO 认证成功后的重定向目标。
     *
     * @param request HTTP 请求
     * @return 完整的回调 URL
     */
    private String buildCallbackUrl(ServerRequest request) {
        String scheme = request.uri().getScheme();
        String host = request.uri().getHost();
        int port = request.uri().getPort();
        String portPart = (port == 80 || port == 443 || port == -1) ? "" : ":" + port;

        return scheme + "://" + host + portPart + "/auth/sso/callback";
    }
}
