package com.apex.scaffold.application.service.login;

import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.application.service.auth.AuthNextAction;
import com.apex.scaffold.application.service.auth.AuthTicketPayload;
import com.apex.scaffold.application.service.auth.AuthTicketService;
import com.apex.scaffold.application.service.auth.AuthTicketType;
import com.apex.scaffold.application.service.auth.RegisterChannelResolver;
import com.apex.scaffold.common.dto.TokenStoreEntry;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.service.TokenService;
import reactor.core.publisher.Mono;

/**
 * 手机号登录策略抽象基类。
 *
 * <p>采用模板方法模式：子类只实现 {@link #authenticate(LoginRequest)} 取得已验证的手机号，
 * 用户查询、注册准入校验、注册票据签发、令牌发放与响应构建由本基类统一完成。
 *
 * <h2>流程</h2>
 * <ol>
 *   <li>调用子类 {@link #authenticate(LoginRequest)} 获取已验证手机号</li>
 *   <li>按手机号查询已有用户（{@link UserService#getByPhone(String)}）</li>
 *   <li>用户已存在：执行 {@link #postAuthentication(UserEntity, LoginRequest)} 扩展点后
 *       签发正式 accessToken/refreshToken，返回登录响应（{@code isNewUser=false}）</li>
 *   <li>用户不存在：先经注册准入校验，再签发 {@link AuthTicketType#REGISTRATION}
 *       一次性注册票据，并发放与该票据同有效期的临时 accessToken，
 *       返回 {@code nextAction=REGISTER_REQUIRED} 的响应。
 *       <strong>本基类不创建用户</strong>——账号由注册流程消费该票据后落库</li>
 * </ol>
 *
 * <h2>扩展点</h2>
 * <ul>
 *   <li>{@link #postAuthentication(UserEntity, LoginRequest)}：用户已存在时、构建登录响应前的
 *       异步钩子，子类可重写以同步外部资料（如微信昵称、头像）</li>
 * </ul>
 *
 * <h2>已知实现</h2>
 * <ul>
 *   <li>{@link SmsLoginStrategy} - 短信验证码登录</li>
 *   <li>{@link ChuanglanSmsLoginStrategy} - 创蓝短信验证码登录</li>
 *   <li>{@link ChuanglanPhoneLoginStrategy} - 创蓝一键登录</li>
 *   <li>{@link WeChatPhoneLoginStrategy} - 微信一键登录</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see LoginStrategy
 * @see LoginResponseHelper
 * @see #authenticate(LoginRequest)
 */
public abstract class AbstractPhoneLoginStrategy implements LoginStrategy {

    /** 用户应用服务，提供用户查询和创建功能 */
    protected final UserService userService;

    /** Token 服务，提供 JWT 生成和存储功能 */
    protected final TokenService tokenService;

    /** 登录响应构建器，负责构建统一格式的登录响应 */
    protected final LoginResponseHelper loginResponseHelper;

    /**
     * 注册前一次性票据服务。
     *
     * <p>用于在手机号验证通过但用户尚未注册时签发临时凭证，
     * 注册完成后一次性消费，防止票据重复使用。</p>
     */
    protected final AuthTicketService authTicketService;

    /**
     * 创建手机号登录策略基类实例。
     *
     * @param userService         用户应用服务，用于按手机号查询用户，并校验注册准入
     * @param tokenService        令牌服务，用于签发与存储 accessToken/refreshToken
     * @param loginResponseHelper 登录响应构建器，统一响应字段与令牌对结构
     * @param authTicketService   一次性票据服务，新用户登录时签发注册票据
     */
    protected AbstractPhoneLoginStrategy(
            UserService userService,
            TokenService tokenService,
            LoginResponseHelper loginResponseHelper,
            AuthTicketService authTicketService) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.loginResponseHelper = loginResponseHelper;
        this.authTicketService = authTicketService;
    }

    /**
     * 执行手机号登录流程（模板方法）
     *
     * <p>定义了完整的登录流程骨架：
     * <ol>
     *   <li>调用 {@link #authenticate(LoginRequest)} 获取已认证的手机号</li>
     *   <li>根据手机号查询已有用户</li>
     *   <li>用户已存在：生成 JWT Token 对，返回完整登录响应（isNewUser=false）</li>
     *   <li>用户不存在（新用户）：签发一次性 authTicket 用于注册，同时发放
     *       有效期与 authTicket 一致的临时 accessToken，避免客户端在注册流程中
     *       因缺少 Token 而收到 401 被强制跳转登录页</li>
     * </ol>
     *
     * @param request 登录请求参数
     * @return 登录响应 Mono
     */
    @Override
    public Mono<LoginResponse> login(LoginRequest request) {
        String deviceId = request.getDeviceId();
        return authenticate(request).flatMap(phone -> userService
                .getByPhone(phone)
                .flatMap(existing -> postAuthentication(existing, request)
                        .then(Mono.defer(() -> buildLoginResponse(existing, phone, false, deviceId, request))))
                .switchIfEmpty(Mono.defer(
                        () -> userService.assertPhoneRegistrationAllowed(phone).then(Mono.defer(() -> {
                            AuthTicketPayload payload = new AuthTicketPayload();
                            payload.setType(AuthTicketType.REGISTRATION);
                            payload.setDeviceId(deviceId);
                            payload.setPhone(phone);
                            // 记录注册来源：注册落库时写入 sys_user.register_channel
                            payload.setRegisterChannel(
                                    RegisterChannelResolver.resolve(request).name());
                            return authTicketService.issue(payload).map(ticket -> {
                                // 签发与 authTicket 有效期一致的临时 accessToken，
                                // 确保新用户在注册流程中不会因 Token 缺失而被 401 强制登出
                                String tempAccessToken = tokenService.generateAccessToken(phone, deviceId);
                                tokenService.storeAccessToken(
                                        phone, tempAccessToken, (int) AuthTicketService.EXPIRES_IN_SECONDS);
                                LoginResponse response = new LoginResponse();
                                response.setNextAction(AuthNextAction.REGISTER_REQUIRED.name());
                                response.setAuthTicket(ticket);
                                response.setAuthTicketExpiresIn(AuthTicketService.EXPIRES_IN_SECONDS);
                                response.setAccessToken(tempAccessToken);
                                response.setTokenType("Bearer");
                                response.setExpiresIn((long) AuthTicketService.EXPIRES_IN_SECONDS);
                                return response;
                            });
                        })))));
    }

    /**
     * 执行手机号认证并获取用户手机号
     *
     * <p>子类需实现此方法，通过各自的认证渠道（如短信验证码验证、一键登录token置换等）
     * 获取经过认证的用户手机号。
     *
     * @param request 登录请求参数，包含认证所需的凭证信息
     * @return 经过认证的手机号 Mono
     */
    protected abstract Mono<String> authenticate(LoginRequest request);

    /**
     * 认证成功后置处理（钩子方法，支持异步操作）。
     *
     * <p>子类可重写此方法在查询到已有用户后、构建登录响应前执行额外的异步业务逻辑，
     * 如 {@link WeChatPhoneLoginStrategy} 中利用用户已存储的微信 access_token 同步
     * 微信用户资料（昵称、头像、性别等）。基类返回 {@link Mono#empty()}，子类按需重写。
     *
     * <p>该方法在 {@link #login(LoginRequest)} 模板方法中于 {@code buildLoginResponse}
     * 之前调用，确保资料同步完成后再构建包含最新信息的登录响应。</p>
     *
     * @param user    查询到的已有用户实体
     * @param request 原始登录请求参数
     */
    protected Mono<Void> postAuthentication(UserEntity user, LoginRequest request) {
        return Mono.empty();
    }

    /**
     * 构建登录响应
     *
     * <p>生成 JWT Token 对（accessToken + refreshToken），存储 Token 及用户信息，
     * 并通过 {@link LoginResponseHelper} 构建统一格式的登录响应。
     * 多会话模型：每次登录生成新会话ID（{@code sid}），令牌按会话槽独立存储，
     * 多端同时在线互不覆盖；客户端类型取请求头/请求体上报值（缺省 UNKNOWN）。
     *
     * @param user      用户实体
     * @param phone     用户手机号
     * @param isNewUser 是否为新注册用户
     * @param deviceId  设备标识，绑定Token与设备
     * @param request   登录请求参数，用于提取客户端IP
     * @return 包含 JWT Token 对和用户信息的登录响应 Mono
     */
    protected Mono<LoginResponse> buildLoginResponse(
            UserEntity user, String phone, boolean isNewUser, String deviceId, LoginRequest request) {
        String subject = user.getOpenId() != null ? user.getOpenId() : phone;
        String sid = java.util.UUID.randomUUID().toString();
        String clientType = com.apex.scaffold.common.enums.ClientType.fromCode(request.getClientType())
                .name();
        String accessToken = tokenService.generateAccessToken(subject, deviceId, sid, clientType);
        String refreshToken = tokenService.generateRefreshToken(subject, deviceId, sid, clientType);
        TokenStoreEntry tokenEntry = buildTokenEntry(user, sid, clientType, request);
        return userService
                .updateLoginInfo(user.getId(), request.getClientIp())
                .then(tokenService.storeSessionToken(subject, sid, accessToken, refreshToken, tokenEntry, tokenEntry))
                .thenReturn(loginResponseHelper.build(
                        user, accessToken, refreshToken, isNewUser, phone, request.getClientIp()));
    }

    /**
     * 从用户实体构建 Token 存储条目（多会话模型，携带会话与终端上下文）。
     *
     * @param user       用户实体
     * @param sid        会话ID
     * @param clientType 客户端类型编码
     * @param request    登录请求（提取 platform / loginType）
     * @return 包含用户基础信息与会话上下文的 TokenStoreEntry
     */
    private TokenStoreEntry buildTokenEntry(UserEntity user, String sid, String clientType, LoginRequest request) {
        return TokenStoreEntry.builder()
                .userId(user.getId())
                .openId(user.getOpenId())
                .nickName(user.getNickName())
                .avatarUrl(user.getAvatarUrl())
                .gender(user.getGender())
                .phone(user.getPhone())
                .unionId(user.getUnionId())
                .sid(sid)
                .clientType(clientType)
                .platform(request.getPlatform())
                .loginType(getLoginType().name())
                .deviceId(request.getDeviceId())
                .build();
    }
}
