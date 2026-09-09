package com.apex.scaffold.interfaces.handler;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.dto.MobileValidateResponse;
import com.apex.scaffold.application.dto.RefreshTokenRequest;
import com.apex.scaffold.application.dto.RegisterRequest;
import com.apex.scaffold.application.dto.SendSmsRequest;
import com.apex.scaffold.application.dto.WechatBindPhoneRequest;
import com.apex.scaffold.application.dto.WxPhoneRequest;
import com.apex.scaffold.application.service.AuthRegistrationService;
import com.apex.scaffold.application.service.AuthService;
import com.apex.scaffold.common.annotation.IdempotentApi;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.monitor.BusinessMetricsRecorder;
import com.apex.scaffold.interfaces.monitor.SecurityMetricsRecorder;
import com.apex.scaffold.interfaces.util.MessageUtils;
import com.apex.scaffold.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 认证处理器（函数式端点）
 *
 * <p>继承 {@link BaseHandler}，提供微信 APP 登录、Token 刷新、退出登录等认证相关 RESTful API，
 * 基于 Spring WebFlux 函数式编程模型实现。
 * 集成了国际化支持，错误消息会根据客户端的 Accept-Language 自动切换。
 * 错误响应统一通过 {@link BaseHandler#buildError(Integer, String, String, Object...)} 构建。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>微信 APP 登录：通过微信开放平台授权码换取登录态，返回 JWT Token</li>
 *   <li>Token 刷新：使用 refreshToken 换取新的 accessToken</li>
 *   <li>退出登录：使当前 Token 失效，清除用户登录态</li>
 *   <li>绑定微信手机号：获取并绑定用户微信手机号</li>
 *   <li>发送短信验证码：支持普通短信和创蓝短信平台</li>
 * </ul>
 *
 * <h2>安全特性</h2>
 * <ul>
 *   <li>集成 Sentinel 限流保护登录接口</li>
 *   <li>集成操作日志记录（{@link com.apex.scaffold.common.annotation.OperationLog}）</li>
 *   <li>基于请求头 Authorization 提取 JWT Token</li>
 *   <li>国际化错误消息：限流/参数校验/认证失败消息自动适配客户端语言</li>
 * </ul>
 *
 * @author Apex Team
 * @version 3.0.0
 * @since 1.0.0
 * @see AuthService
 * @see BaseHandler
 * @see RequestHandlerUtil
 */
@Slf4j
@Component
@Tag(name = "认证管理", description = "微信APP登录、Token刷新、退出登录等认证相关接口")
public class AuthHandler extends BaseHandler {

    /** 认证业务服务，处理登录、刷新令牌、登出等认证流程。 */
    private final AuthService authService;

    /** 注册及注册前微信绑定服务，处理新用户注册流程中的微信手机号绑定。 */
    private final AuthRegistrationService authRegistrationService;

    /** 请求处理工具，提供统一的参数校验和响应构建能力 */
    private final RequestHandlerUtil requestHandlerUtil;

    /** 安全指标记录器 */
    private final SecurityMetricsRecorder securityMetricsRecorder;

    /** 核心业务指标记录器。 */
    private final BusinessMetricsRecorder businessMetricsRecorder;

    /**
     * 构造方法，通过 super(messageUtils) 传递 MessageUtils 给 BaseHandler
     *
     * @param authService     认证业务服务
     * @param requestHandlerUtil 请求处理工具
     * @param messageUtils    国际化消息工具
     */
    public AuthHandler(
            AuthService authService,
            AuthRegistrationService authRegistrationService,
            RequestHandlerUtil requestHandlerUtil,
            MessageUtils messageUtils,
            SecurityMetricsRecorder securityMetricsRecorder,
            BusinessMetricsRecorder businessMetricsRecorder) {
        super(messageUtils);
        this.authService = authService;
        this.authRegistrationService = authRegistrationService;
        this.requestHandlerUtil = requestHandlerUtil;
        this.securityMetricsRecorder = securityMetricsRecorder;
        this.businessMetricsRecorder = businessMetricsRecorder;
    }

    /**
     * 微信 APP 登录
     *
     * <p>
     * 处理微信 APP 登录请求，通过微信开放平台授权码换取登录态，返回 JWT Token 和用户信息。
     * 登录方式由 {@link LoginRequest#getLoginType()} 动态决定，支持验证码登录、一键登录和短信验证码登录。
     *
     * <h3>登录方式</h3>
     * <ul>
     *   <li>{@code WX_CODE} - 微信授权码登录</li>
     *   <li>{@code WX_PHONE} - 微信一键登录（手机号）</li>
     *   <li>{@code WX_SMS} - 短信验证码登录</li>
     * </ul>
     *
     * @param request 服务器请求对象，包含登录请求参数（JSON 格式）
     * @return 包含登录结果的响应对象，成功时返回 {@link LoginResponse}（含 JWT Token 和用户信息）
     */
    @Operation(
            summary = "用户登录",
            description = "统一认证入口；支持微信授权码、手机号短信验证码和手机号一键登录。" + "客户端必须根据 data.nextAction 判断是登录完成、注册还是微信绑定手机号。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "认证流程处理成功；具体结果由 data.nextAction 决定",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "微信授权失败",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(module = "认证管理", type = OperationLog.OperationType.LOGIN, description = "用户登录")
    @SentinelResource(value = "authLogin", blockHandler = "handleLoginBlock")
    public Mono<ServerResponse> login(ServerRequest request) {
        log.info("用户登录请求");
        return requestHandlerUtil
                .handleRequestBody(request, LoginRequest.class)
                .flatMap(loginRequest -> {
                    // 提取客户端IP用于安全审计和暴力破解防护
                    String clientIp = requestHandlerUtil.extractClientIp(request);
                    loginRequest.setClientIp(clientIp);
                    return authService.login(loginRequest, clientIp);
                })
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .doOnSuccess(response -> businessMetricsRecorder.recordLoginSuccess())
                .doOnError(error -> securityMetricsRecorder.recordLoginFailed());
    }

    /**
     * 登录接口限流处理（带国际化支持）
     *
     * <p>
     * 当登录请求频率超过 Sentinel 限流阈值时触发。
     * 错误消息通过 {@link MessageUtils} 从 Reactor Context 中获取
     * 客户端语言偏好，返回对应语言的限流提示。
     *
     * @param request 登录请求对象
     * @param ex      限流异常 {@link BlockException}
     * @return 限流响应对象，包含客户端语言对应的限流提示消息
     */
    public Mono<ServerResponse> handleLoginBlock(LoginRequest request, BlockException ex) {
        log.warn("登录接口限流");
        securityMetricsRecorder.recordRateLimitRejected();
        String traceId = TraceIdUtil.getTraceId();
        return buildError(ResultCode.RATE_LIMIT, traceId);
    }

    /**
     * 刷新 Token
     *
     * <p>
     * 使用 refreshToken 换取新的 accessToken，同时延长 refreshToken 有效期。
     * 校验 refreshToken 中的设备指纹与请求设备一致，防止 Token 被跨设备盗用。
     * 此接口无需登录态即可访问，用于客户端在 Token 过期前主动刷新。
     * 参数校验错误消息会根据客户端语言自动切换。
     *
     * @param request 服务器请求对象，请求体为 {@link RefreshTokenRequest}
     *                （含必填 {@code refreshToken} 与可选 {@code deviceId}）
     * @return 包含刷新结果的响应对象，成功时返回新的 {@link LoginResponse}（含新的 Token 对）
     */
    @Operation(
            summary = "刷新Token",
            description = "使用refreshToken换取新的accessToken，延长用户登录态。"
                    + "refreshToken 通过请求体 JSON 传递，避免 Token 出现在 URL 查询参数而被访问日志记录。",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = RefreshTokenRequest.class))))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "刷新成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "RefreshToken已失效",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "认证管理",
            type = OperationLog.OperationType.LOGIN,
            description = "刷新Token",
            saveResponse = false)
    public Mono<ServerResponse> refresh(ServerRequest request) {
        log.info("刷新Token请求");
        return requestHandlerUtil
                .handleRequestBody(request, RefreshTokenRequest.class)
                .flatMap(req -> authService.refreshToken(req.getRefreshToken(), req.getDeviceId()))
                .flatMap(requestHandlerUtil::buildSuccessResponse);
    }

    /**
     * 退出登录
     *
     * <p>
     * 使当前用户的 Token 失效，将其加入黑名单并从存储中删除，清除用户登录态。
     * 认证错误消息会根据客户端语言自动切换。
     * 如果用户未登录（principal 为空），则返回未登录错误。
     *
     * @param request 服务器请求对象，包含 {@code Authorization} 请求头（Bearer Token）
     * @return 包含退出结果的响应对象，成功时返回成功信息
     */
    @Operation(summary = "退出登录", description = "使当前Token失效，清除用户登录态")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "退出成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "未登录或Token已失效",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(module = "认证管理", type = OperationLog.OperationType.LOGOUT, description = "退出登录")
    public Mono<ServerResponse> logout(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        log.info("退出登录请求, traceId: {}", traceId);
        String token = requestHandlerUtil.extractToken(request).orElse(null);
        if (token == null || token.isBlank()) {
            return buildError(ResultCode.UNAUTHORIZED.getCode(), "auth.token_missing", traceId);
        }
        return request.principal()
                .switchIfEmpty(Mono.error(new IllegalStateException("未登录")))
                .map(Principal::getName)
                .flatMap(openId -> authService.logout(token, openId))
                .then(requestHandlerUtil.buildSuccessResponse(null))
                .onErrorResume(
                        IllegalStateException.class,
                        e -> buildError(ResultCode.UNAUTHORIZED.getCode(), "auth.not_logged_in", traceId));
    }

    /**
     * 获取并绑定微信手机号
     *
     * <p>
     * 通过微信手机号授权凭证获取用户微信手机号，并将手机号绑定到当前用户账户。
     * 需用户已登录，从认证信息中获取 OpenID。
     *
     * @param request 服务器请求对象，包含 {@code Authorization} 请求头和手机号授权凭证（JSON 格式）
     * @return 包含绑定结果的响应对象，成功时返回已绑定的手机号
     */
    @Operation(summary = "绑定微信手机号", description = "获取并绑定用户微信手机号")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "绑定成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "未登录",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(module = "认证管理", type = OperationLog.OperationType.UPDATE, description = "绑定微信手机号")
    public Mono<ServerResponse> bindPhone(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        log.info("绑定微信手机号请求, traceId: {}", traceId);
        return requestHandlerUtil
                .handleRequestBody(request, WxPhoneRequest.class)
                .flatMap(wxPhoneRequest -> request.principal()
                        .map(Principal::getName)
                        .flatMap(openId -> authService.bindPhone(wxPhoneRequest, openId)))
                .flatMap(requestHandlerUtil::buildSuccessResponse);
    }

    /**
     * 完成手机号或微信注册，并在成功后返回登录令牌。
     *
     * <p>使用登录或微信绑定流程签发的一次性票据完成用户注册流程。</p>
     *
     * @param request 服务器请求对象，包含注册信息（JSON格式）
     * @return 包含注册结果和登录令牌的响应对象
     */
    @Operation(summary = "用户注册", description = "使用登录或微信绑定流程签发的一次性票据完成注册，注册成功后返回登录令牌")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "注册请求信息",
            content =
                    @Content(mediaType = "application/json", schema = @Schema(implementation = RegisterRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "注册成功，返回登录令牌",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误或票据无效",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(module = "认证管理", type = OperationLog.OperationType.INSERT, description = "用户注册")
    @IdempotentApi(required = false, header = "Idempotency-Key")
    public Mono<ServerResponse> register(ServerRequest request) {
        return requestHandlerUtil
                .handleRequestBody(request, RegisterRequest.class)
                .flatMap(registerRequest -> {
                    registerRequest.setClientIp(requestHandlerUtil.extractClientIp(request));
                    return authRegistrationService.register(registerRequest);
                })
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .doOnSuccess(response -> businessMetricsRecorder.recordRegister());
    }

    /**
     * 在注册前验证微信票据和手机号；已注册手机号直接绑定并登录。
     *
     * <p>微信未绑定账号时验证手机号，已注册则绑定登录，未注册则返回注册票据。</p>
     *
     * @param request 服务器请求对象，包含微信绑定手机号信息（JSON格式）
     * @return 包含绑定或注册票据的响应对象
     */
    @Operation(summary = "微信绑定手机号", description = "微信未绑定账号时验证手机号，已注册则绑定登录，未注册则返回注册票据")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "微信绑定手机号请求信息",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = WechatBindPhoneRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "处理成功，返回绑定结果或注册票据",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误或微信票据无效",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(module = "认证管理", type = OperationLog.OperationType.UPDATE, description = "注册前微信绑定手机号")
    public Mono<ServerResponse> bindWechatPhone(ServerRequest request) {
        return requestHandlerUtil
                .handleRequestBody(request, WechatBindPhoneRequest.class)
                .flatMap(bindRequest -> {
                    bindRequest.setClientIp(requestHandlerUtil.extractClientIp(request));
                    return authRegistrationService.bindWechatPhone(bindRequest);
                })
                .flatMap(requestHandlerUtil::buildSuccessResponse);
    }

    /**
     * 发送短信验证码
     *
     * <p>
     * 向指定手机号发送短信验证码，用于短信验证码登录场景。
     * 验证码有效期为 5 分钟，同一手机号再次发送会覆盖之前的验证码。
     *
     * @param request 服务器请求对象，包含手机号信息（JSON 格式）
     * @return 包含发送结果的响应对象
     */
    @Operation(summary = "发送短信验证码", description = "向指定手机号发送短信验证码，用于短信验证码登录")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "发送成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(module = "认证管理", type = OperationLog.OperationType.OTHER, description = "发送短信验证码")
    public Mono<ServerResponse> sendSmsCode(ServerRequest request) {
        log.info("发送短信验证码请求");
        return requestHandlerUtil
                .handleRequestBody(request, SendSmsRequest.class)
                .flatMap(sendSmsRequest -> authService.sendSmsCode(sendSmsRequest))
                .then(requestHandlerUtil.buildSuccessResponse(null));
    }

    /**
     * 发送创蓝短信验证码
     *
     * <p>
     * 通过创蓝 253 短信平台向指定手机号发送验证码，用于创蓝短信验证码登录场景。
     * 验证码有效期为 5 分钟，同一手机号再次发送会覆盖之前的验证码。
     *
     * @param request 服务器请求对象，包含手机号信息（JSON 格式）
     * @return 包含发送结果的响应对象
     */
    @Operation(summary = "发送创蓝短信验证码", description = "通过创蓝253短信平台向指定手机号发送验证码，用于创蓝短信验证码登录")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "发送成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(module = "认证管理", type = OperationLog.OperationType.OTHER, description = "发送创蓝短信验证码")
    public Mono<ServerResponse> sendClSmsCode(ServerRequest request) {
        log.info("发送创蓝短信验证码请求");
        return requestHandlerUtil
                .handleRequestBody(request, SendSmsRequest.class)
                .flatMap(sendSmsRequest -> authService.sendClSmsCode(sendSmsRequest))
                .then(requestHandlerUtil.buildSuccessResponse(null));
    }

    /**
     * 本机号校验
     *
     * <p>
     * 调用创蓝闪验本机号校验API，校验当前流量卡的手机号与传入的手机号码是否一致。
     * 该接口适用于需要验证用户当前使用的手机号是否为其SIM卡绑定手机号的场景。
     *
     * <h3>业务场景</h3>
     * <ul>
     *   <li>用户身份验证：确认用户正在使用的手机号与注册手机号一致</li>
     *   <li>安全校验：在敏感操作前验证用户身份</li>
     *   <li>一键登录降级：当一键登录不可用时，验证用户输入的手机号是否为本机号</li>
     * </ul>
     *
     * <h3>请求参数</h3>
     * <table border="1">
     *   <tr><th>参数名</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>token</td><td>String</td><td>是</td><td>本机认证SDK返回的临时令牌，一次有效</td></tr>
     *   <tr><td>mobile</td><td>String</td><td>是</td><td>待校验的手机号码</td></tr>
     *   <tr><td>outId</td><td>String</td><td>否</td><td>客户方流水号，用于业务追踪</td></tr>
     * </table>
     *
     * <h3>响应字段</h3>
     * <table border="1">
     *   <tr><th>字段名</th><th>类型</th><th>说明</th></tr>
     *   <tr><td>isVerify</td><td>String</td><td>校验结果："1"表示是本机号码，"0"表示非本机号码</td></tr>
     *   <tr><td>tradeNo</td><td>String</td><td>交易流水号</td></tr>
     *   <tr><td>success</td><td>Boolean</td><td>是否校验成功</td></tr>
     * </table>
     *
     * @param request 服务器请求对象，包含表单参数（application/x-www-form-urlencoded）
     * @return 包含校验结果的响应对象
     * @see <a href="https://doc.chuanglan.com/document/SY88R0I8TCUPR1M3">创蓝闪验SDK服务端接口文档</a>
     */
    @Operation(summary = "本机号校验", description = "调用创蓝闪验本机号校验API，校验当前流量卡的手机号与传入的手机号码是否一致")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "校验成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MobileValidateResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(module = "认证管理", type = OperationLog.OperationType.OTHER, description = "本机号校验")
    public Mono<ServerResponse> validateMobile(ServerRequest request) {
        log.info("本机号校验请求");
        return request.formData().flatMap(formData -> {
            String token = formData.getFirst("token");
            String mobile = formData.getFirst("mobile");
            String outId = formData.getFirst("outId");
            return authService.validateMobile(token, mobile, outId).flatMap(requestHandlerUtil::buildSuccessResponse);
        });
    }
}
