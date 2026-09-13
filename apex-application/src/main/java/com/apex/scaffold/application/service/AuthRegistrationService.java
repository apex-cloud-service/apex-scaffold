package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.dto.RegisterRequest;
import com.apex.scaffold.application.dto.WechatBindPhoneRequest;
import com.apex.scaffold.application.service.auth.AuthNextAction;
import com.apex.scaffold.application.service.auth.AuthTicketPayload;
import com.apex.scaffold.application.service.auth.AuthTicketService;
import com.apex.scaffold.application.service.auth.AuthTicketType;
import com.apex.scaffold.application.service.login.LoginResponseHelper;
import com.apex.scaffold.common.dto.TokenStoreEntry;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.service.SmsCodeService;
import com.apex.scaffold.domain.service.TokenService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 用户注册及微信绑定编排服务。
 *
 *
 *<h2>职责</h2>
 *<p>该服务负责编排以下核心业务流程：</p>
 *<ol>
 *  <li><b>注册</b> — 消耗 {@link AuthTicketType#REGISTRATION} 票据，
 *      根据性别 / 生日合法性校验创建完整用户并签发 JWT Token 自动登录</li>
 *  <li><b>微信手机号绑定</b> — 消耗 {@link AuthTicketType#WECHAT_BIND} 票据，
 *      在微信授权码登录无手机号场景下将微信 openId 与短信验证后的手机号绑定</li>
 *  <li><b>跨身份合并</b> — 当微信绑定的手机号已存在独立手机号注册用户时，
 *      将微信 openId / unionId / wechatToken 合并到已有手机号用户上，并清理旧的占位 openId</li>
 *</ol>
 *
 *<h2>调用链路</h2>
 *<pre>
 *  AuthHandler
 *    └── AuthRegistrationService
 *          ├── register()           → POST /api/v1/auth/register
 *          └── bindWechatPhone()    → POST /api/v1/auth/bind-wechat-phone
 *                ├── AuthTicketService.consume()
 *                ├── UserService.getByOpenId() / getByPhone()
 *                ├── UserService.create() / update()
 *                └── TokenService.generateAccessToken() / storeToken()
 *</pre>
 *
 *<h2>安全保障</h2>
 *<ul>
 *  <li>所有请求依赖一次性票据（{@link AuthTicketService}），防止重放攻击</li>
 *  <li>短信验证码通过 {@link SmsCodeService#validate(String, String)} 校验，
 *      由调用方（AuthHandler）在 {@code bindWechatPhone} 入口传入</li>
 *  <li>性别和生日在注册阶段强制执行校验（{@link #validateRegistrationProfile(RegisterRequest)}），
 *      防止非法数据进入数据库</li>
 *  <li>手机号唯一性在 {@link UserService} 层面通过数据库 UNIQUE 约束保障</li>
 *  <li>微信 openId 与手机号的绑定关系严格检查：
 *      <ul>
 *        <li>同一 openId 不允许绑定多个不同手机号</li>
 *        <li>同一手机号不允许绑定多个不同 openId</li>
 *      </ul>
 *  </li>
 *</ul>
 *
 *@see com.apex.scaffold.application.service.auth.AuthTicketService
 *@see com.apex.scaffold.application.service.auth.AuthTicketType
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Service
/**n * AuthRegistrationServicen *n * <p>TODO: 补充服务的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class AuthRegistrationService {

    /** 用户持久化服务，提供按 openId / phone 查询及创建 / 更新能力。 */
    private final UserService userService;

    /** 一次性票据服务，负责票据的签发、校验和消耗。 */
    private final AuthTicketService authTicketService;

    /** 短信验证码服务（创蓝实现），用于绑定手机号前的二次验证。 */
    private final SmsCodeService smsCodeService;

    /** JWT Token 服务，负责双 Token（accessToken + refreshToken）的生成与存储。 */
    private final TokenService tokenService;

    /** 登录响应构建器，将用户实体 + Token 组装为统一的 {@link LoginResponse}。 */
    private final LoginResponseHelper loginResponseHelper;

    /**
     * 构造注册服务。
     *
     * @param userService         用户持久化服务
     * @param authTicketService   一次性票据服务
     * @param smsCodeService      短信验证码服务（通过 {@code @Qualifier("chuanglanSmsCodeService")} 注入）
     * @param tokenService        JWT Token 服务
     * @param loginResponseHelper 登录响应构建器
     */
    public AuthRegistrationService(
            UserService userService,
            AuthTicketService authTicketService,
            @Qualifier("chuanglanSmsCodeService") SmsCodeService smsCodeService,
            TokenService tokenService,
            LoginResponseHelper loginResponseHelper) {
        this.userService = userService;
        this.authTicketService = authTicketService;
        this.smsCodeService = smsCodeService;
        this.tokenService = tokenService;
        this.loginResponseHelper = loginResponseHelper;
    }

    /**
     * 绑定微信手机号。
     *
     * <p>该方法是微信授权码登录流程的第二步，适用于以下场景：
     * <ul>
     *   <li>用户首次登录：微信授权码登录后获得 {@code WECHAT_BIND} 票据，
     *       用户输入手机号 + 短信验证码后调用本方法完成绑定</li>
     *   <li>手机号已注册（独立手机号登录）：直接合并微信 openId 到已有账号并登录</li>
     *   <li>手机号未注册：签发 {@code REGISTRATION} 票据，
     *       由客户端引导用户进入注册页</li>
     * </ul>
     *
     * <h3>业务流程</h3>
     * <ol>
     *   <li>调用 {@link SmsCodeService#validate(String, String)} 校验短信验证码</li>
     *   <li>验证通过后消耗 {@code WECHAT_BIND} 票据，获取微信授权的 openId / unionId / wechatToken</li>
     *   <li>进入 {@link #bindVerifiedPhone(AuthTicketPayload, WechatBindPhoneRequest)}
     *       执行微信 openId 与手机号的合并或注册票据签发</li>
     * </ol>
     *
     * @param request 微信手机号绑定请求，包含 wechatTicket / phone / smsCode
     * @return 绑定 / 合并成功时返回含 JWT Token 的登录响应；
     *         手机号未注册时返回含 {@code REGISTRATION} 票据的响应（{@code nextAction=REGISTER_REQUIRED}）
     * @throws BusinessException 验证码错误或已过期时抛出（code={@link ResultCode#PARAM_ERROR}）
     */
    public Mono<LoginResponse> bindWechatPhone(WechatBindPhoneRequest request) {
        return smsCodeService
                .validate(request.getPhone(), request.getSmsCode())
                .flatMap(valid -> valid
                        ? authTicketService.execute(
                                request.getWechatTicket(),
                                AuthTicketType.WECHAT_BIND,
                                request.getDeviceId(),
                                LoginResponse.class,
                                payload -> bindVerifiedPhone(payload, request))
                        : Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "验证码错误或已过期")));
    }

    /**
     * 完成用户注册并自动登录。
     *
     * <p>消耗前端传入的 {@code REGISTRATION} 票据后，根据票据中是否携带微信 openId
     * 决定用户实体创建策略：
     * <ul>
     *   <li><b>有微信 openId</b> — 检查该 openId 是否已有未绑定手机号的空壳用户：
     *       <ul>
     *         <li>存在空壳用户 → 通过 {@link #applyRegistration(UserEntity, AuthTicketPayload, RegisterRequest)}
     *             补全用户信息（phone / gender / nickname 等），执行 {@code userService.update()}</li>
     *         <li>不存在 → 创建新用户，openId 使用微信真实值</li>
     *       </ul>
     *   </li>
     *   <li><b>无微信 openId</b> — 直接创建新用户，openId 使用 {@code PHONE_} 前缀占位</li>
     * </ul>
     *
     * <h3>前置校验</h3>
     * <p>调用 {@link #validateRegistrationProfile(RegisterRequest)} 校验：
     * <ul>
     *   <li>{@code gender} 必须是 1（男）或 2（女）</li>
     *   <li>{@code birthday} 必须不为空且早于当前日期</li>
     * </ul>
     * 校验不通过时抛出 {@link BusinessException}（code={@link ResultCode#PARAM_ERROR}）。</p>
     *
     * @param request 注册请求，包含 registrationTicket / gender / birthday / nickName / deviceId / clientIp
     * @return 含 JWT Token 和 {@code isNewUser=true} 的登录响应
     * @throws BusinessException 性别或生日不合法时抛出，手机号已注册时抛出（code={@link ResultCode#USER_ALREADY_EXISTS}）
     */
    public Mono<LoginResponse> register(RegisterRequest request) {
        validateRegistrationProfile(request);
        return authTicketService.execute(
                request.getRegistrationTicket(),
                AuthTicketType.REGISTRATION,
                request.getDeviceId(),
                LoginResponse.class,
                payload -> registerWithPayload(payload, request));
    }

    /**
     * 使用注册票据载荷完成注册或登录。
     *
     * <p>按票据中的手机号查找已有用户：已存在且身份一致则直接登录，
     * 身份不一致则报手机号已注册错误；不存在则创建新用户并登录。</p>
     *
     * @param payload 注册票据载荷（含手机号与 openId）
     * @param request 注册请求
     * @return 注册或登录响应
     */
    private Mono<LoginResponse> registerWithPayload(AuthTicketPayload payload, RegisterRequest request) {
        return userService
                .getByPhone(payload.getPhone())
                .flatMap(existing -> isSameRegistrationIdentity(existing, payload)
                        ? buildLoginResponse(existing, request.getDeviceId(), request.getClientIp(), true)
                        : Mono.<LoginResponse>error(new BusinessException(ResultCode.USER_ALREADY_EXISTS)))
                .switchIfEmpty(Mono.defer(() -> completeOrCreateUser(payload, request)
                        .flatMap(
                                user -> buildLoginResponse(user, request.getDeviceId(), request.getClientIp(), true))));
    }

    /**
     * 在短信验证通过后，执行微信 openId 与已验证手机号的绑定决策。
     *
     * <p>决策逻辑分两步：
     * <ol>
     *   <li>按微信 openId 查找已有用户（{@code openUser}），检查是否已绑定其他手机号</li>
     *   <li>进入 {@link #bindAgainstPhoneUser(AuthTicketPayload, WechatBindPhoneRequest, UserEntity)}
     *       按手机号查找已有用户（{@code phoneUser}），执行合并或签发注册票据</li>
     * </ol>
     *
     * @param payload 微信一次性票据载荷（含 openId / unionId / wechatToken）
     * @param request 微信手机号绑定请求（含 phone）
     * @return 绑定 / 合并成功时返回登录响应，手机号未注册时返回注册票据响应
     * @throws BusinessException openId 已绑定到其他手机号时抛出（code={@link ResultCode#WECHAT_ALREADY_BOUND}）
     */
    private Mono<LoginResponse> bindVerifiedPhone(AuthTicketPayload payload, WechatBindPhoneRequest request) {
        String openId = payload.getOpenId();
        return userService
                .getByOpenId(openId)
                .flatMap(openUser -> {
                    if (openUser.getPhone() != null
                            && !openUser.getPhone().isBlank()
                            && !openUser.getPhone().equals(request.getPhone())) {
                        return Mono.<LoginResponse>error(new BusinessException(ResultCode.WECHAT_ALREADY_BOUND));
                    }
                    return bindAgainstPhoneUser(payload, request, openUser);
                })
                .switchIfEmpty(Mono.defer(() -> userService
                        .assertWechatRegistrationAllowed(openId)
                        .then(bindAgainstPhoneUser(payload, request, null))));
    }

    /**
     * 按手机号查找已有用户并执行合并或签发注册票据。
     *
     * <h3>决策分支</h3>
     * <ul>
     *   <li><b>手机号已注册（phoneUser 存在）</b>：
     *       <ul>
     *         <li>{@code openUser} 和 {@code phoneUser} 是不同用户 → 拒绝绑定
     *             （code={@link ResultCode#WECHAT_ALREADY_BOUND}）</li>
     *         <li>相同用户或 {@code openUser} 为空 → 调用 {@link #bindAndLogin(UserEntity, AuthTicketPayload, WechatBindPhoneRequest)}
     *             将微信 openId / unionId / Token 合并到已有手机号用户上</li>
     *       </ul>
     *   </li>
     *   <li><b>手机号未注册</b> → 调用 {@link #issueRegistrationTicket(AuthTicketPayload, String)}
     *       签发 {@code REGISTRATION} 票据，由客户端引导用户注册</li>
     * </ul>
     *
     * @param payload  微信一次性票据载荷
     * @param request  微信手机号绑定请求
     * @param openUser 按 openId 查到的已有用户，不存在时为 {@code null}
     * @return 绑定 / 合并成功时返回登录响应，手机号未注册时返回注册票据响应
     * @throws BusinessException 手机号已被其他 openId 占用时抛出（code={@link ResultCode#PHONE_ALREADY_BOUND}）
     */
    private Mono<LoginResponse> bindAgainstPhoneUser(
            AuthTicketPayload payload, WechatBindPhoneRequest request, UserEntity openUser) {
        return userService
                .getByPhone(request.getPhone())
                .flatMap(phoneUser -> {
                    if (openUser != null && !openUser.getId().equals(phoneUser.getId())) {
                        return Mono.<LoginResponse>error(new BusinessException(ResultCode.WECHAT_ALREADY_BOUND));
                    }
                    String currentOpenId = phoneUser.getOpenId();
                    if (isRealWechatOpenId(currentOpenId) && !currentOpenId.equals(payload.getOpenId())) {
                        return Mono.<LoginResponse>error(new BusinessException(ResultCode.PHONE_ALREADY_BOUND));
                    }
                    return bindAndLogin(phoneUser, payload, request);
                })
                .switchIfEmpty(Mono.defer(() -> userService
                        .assertPhoneRegistrationAllowed(request.getPhone())
                        .then(userService.assertWechatRegistrationAllowed(payload.getOpenId()))
                        .then(issueRegistrationTicket(payload, request.getPhone()))));
    }

    /**
     * 将微信授权信息合并到已有手机号用户上并完成登录。
     *
     * <h3>操作步骤</h3>
     * <ol>
     *   <li>将微信 openId / unionId / wechatToken 写入用户实体</li>
     *   <li>如果用户之前的 openId 是 {@code PHONE_} 占位符，清理 Redis 中旧的 Token 缓存</li>
     *   <li>调用 {@link UserService#update(UserEntity)} 持久化变更</li>
     *   <li>签发新的 accessToken + refreshToken</li>
     * </ol>
     *
     * <h3>幂等性保障</h3>
     * <p>{@code UserAssembler.mergeUpdate} 使用
     * {@code NullValuePropertyMappingStrategy.IGNORE} 策略：
     * 仅当 source 字段为非 {@code null} 时才覆盖 target 字段，避免了重复绑定时丢失已有数据。
     * 具体到本方法：openId / unionId 每次都是从票据中解出的真实值，必定非 null，
     * 因此多次绑定同一个微信账号不会产生副作用。</p>
     *
     * @param user    已有手机号用户实体
     * @param payload 一次性票据载荷（含 openId / unionId / wechatToken）
     * @param request 微信手机号绑定请求（含 phone / deviceId / clientIp）
     * @return 含 JWT Token 和 {@code isNewUser=false} 的登录响应
     */
    private Mono<LoginResponse> bindAndLogin(
            UserEntity user, AuthTicketPayload payload, WechatBindPhoneRequest request) {
        String oldSubject = user.getOpenId();
        user.setOpenId(payload.getOpenId());
        user.setUnionId(payload.getUnionId());
        user.setPhone(request.getPhone());
        user.updateToken(
                payload.getWechatAccessToken(), payload.getWechatRefreshToken(), payload.getWechatTokenExpireTime());
        Mono<Void> invalidateOldToken = oldSubject != null && !oldSubject.isBlank() && isPlaceholderOpenId(oldSubject)
                ? tokenService.deleteToken(oldSubject).then()
                : Mono.empty();
        return invalidateOldToken
                .then(userService.update(user))
                .flatMap(updated -> buildLoginResponse(updated, request.getDeviceId(), request.getClientIp(), false));
    }

    /**
     * 签发注册票据。
     *
     * <p>当手机号未注册时，将微信授权信息（openId / unionId / wechatToken）
     * 与已验证的手机号打包为 {@link AuthTicketType#REGISTRATION} 票据，
     * 交由客户端引导用户进入注册页。</p>
     *
     * <p>注册时必须携带此票据，服务端通过 {@code consume(REGISTRATION)} 获取
     * 手机号和微信授权信息，确保注册链路的安全性（防止手机号伪造）。</p>
     *
     * @param wechat 微信一次性票据载荷（openId / unionId / wechatToken）
     * @param phone  已验证的手机号
     * @return 含 {@code nextAction=REGISTER_REQUIRED} 和注册票据的登录响应
     * @see AuthTicketType#REGISTRATION
     */
    private Mono<LoginResponse> issueRegistrationTicket(AuthTicketPayload wechat, String phone) {
        AuthTicketPayload registration = new AuthTicketPayload();
        registration.setType(AuthTicketType.REGISTRATION);
        registration.setDeviceId(wechat.getDeviceId());
        registration.setPhone(phone);
        registration.setOpenId(wechat.getOpenId());
        registration.setUnionId(wechat.getUnionId());
        registration.setWechatAccessToken(wechat.getWechatAccessToken());
        registration.setWechatRefreshToken(wechat.getWechatRefreshToken());
        registration.setWechatTokenExpireTime(wechat.getWechatTokenExpireTime());
        // 将微信用户资料（昵称、头像、地区等）从 WECHAT_BIND 完整传递到 REGISTRATION 票据
        registration.setNickName(wechat.getNickName());
        registration.setAvatarUrl(wechat.getAvatarUrl());
        registration.setGender(wechat.getGender());
        registration.setCountry(wechat.getCountry());
        registration.setProvince(wechat.getProvince());
        registration.setCity(wechat.getCity());
        registration.setLanguage(wechat.getLanguage());
        return authTicketService.issue(registration).map(ticket -> {
            LoginResponse response = new LoginResponse();
            response.setNextAction(AuthNextAction.REGISTER_REQUIRED.name());
            response.setAuthTicket(ticket);
            response.setAuthTicketExpiresIn(AuthTicketService.EXPIRES_IN_SECONDS);
            return response;
        });
    }

    /**
     * 补全已有空壳用户或创建全新用户。
     *
     * <h3>分支决策</h3>
     * <ul>
     *   <li><b>票据中有微信 openId</b>：
     *       <ul>
     *         <li>按 openId 查到已有用户且无手机号（空壳）→ 调用
     *             {@link #applyRegistration(UserEntity, AuthTicketPayload, RegisterRequest)} 补全信息</li>
     *         <li>按 openId 查到已有用户且已绑定手机号 → 拒绝注册
     *             （code={@link ResultCode#USER_ALREADY_EXISTS}）</li>
     *         <li>按 openId 未查到 → 通过 {@link #newRegisteredUser(AuthTicketPayload, RegisterRequest)} 创建新用户</li>
     *       </ul>
     *   </li>
     *   <li><b>票据中无微信 openId</b>（独立手机号注册）→ 直接创建新用户</li>
     * </ul>
     *
     * @param payload 注册票据载荷（含 phone / openId / unionId / wechatToken）
     * @param request 注册请求（含 gender / birthday / nickName）
     * @return 创建或更新后的用户实体
     * @throws BusinessException openId 对应的用户已绑定手机号时抛出
     */
    private Mono<UserEntity> completeOrCreateUser(AuthTicketPayload payload, RegisterRequest request) {
        if (payload.getOpenId() == null || payload.getOpenId().isBlank()) {
            return userService.create(newRegisteredUser(payload, request));
        }
        return userService
                .getByOpenId(payload.getOpenId())
                .flatMap(existing -> {
                    if (existing.getPhone() != null && !existing.getPhone().isBlank()) {
                        return Mono.<UserEntity>error(new BusinessException(ResultCode.USER_ALREADY_EXISTS));
                    }
                    applyRegistration(existing, payload, request);
                    return userService.update(existing);
                })
                .switchIfEmpty(Mono.defer(() -> userService.create(newRegisteredUser(payload, request))));
    }

    /**
     * 构建待持久化的全新用户实体。
     *
     * <h3>初始化字段</h3>
     * <ul>
     *   <li>{@code openId} — 票据中有真实微信 openId 则使用，否则使用 {@code PHONE_ + UUID} 占位</li>
     *   <li>{@code status} — 设为 1（ENABLED）</li>
     *   <li>{@code onboardingStatus} — 设为 "ONBOARDING_PENDING"</li>
     *   <li>{@code deleted} — 设为 0</li>
     *   <li>{@code createTime} / {@code updateTime} — 设为当前时间</li>
     *   <li>其余字段由 {@link #applyRegistration(UserEntity, AuthTicketPayload, RegisterRequest)} 填充</li>
     * </ul>
     *
     * @param payload 注册票据载荷
     * @param request 注册请求
     * @return 已设置基础字段的新用户实体（含 phone / gender / birthday / nickName / language）
     * @see #applyRegistration(UserEntity, AuthTicketPayload, RegisterRequest)
     */
    private UserEntity newRegisteredUser(AuthTicketPayload payload, RegisterRequest request) {
        UserEntity user = new UserEntity();
        user.setOpenId(
                payload.getOpenId() == null || payload.getOpenId().isBlank()
                        ? "PHONE_" + java.util.UUID.randomUUID().toString().replace("-", "")
                        : payload.getOpenId());
        user.setStatus(1);
        user.setOnboardingStatus("ONBOARDING_PENDING");
        user.setDeleted(0);
        LocalDateTime now = LocalDateTime.now();
        user.setCreateTime(now);
        user.setUpdateTime(now);
        applyRegistration(user, payload, request);
        return user;
    }

    /**
     * 将注册表单和票据信息写入用户实体。
     *
     * <h3>填充的字段</h3>
     * <table>
     *   <tr><th>字段</th><th>来源</th><th>说明</th></tr>
     *   <tr><td>{@code phone}</td><td>{@code payload.phone}（票据校验时已验证）</td><td>手机号</td></tr>
     *   <tr><td>{@code unionId}</td><td>{@code payload.unionId}</td><td>微信开放平台唯一标识</td></tr>
     *   <tr><td>{@code gender}</td><td>{@code request.gender}（1=男, 2=女）</td><td>性别</td></tr>
     *   <tr><td>{@code birthday}</td><td>{@code request.birthday}</td><td>出生日期</td></tr>
     *   <tr><td>{@code nickName}</td><td>{@code request.nickName} 或自动生成 "Apex用户XXXXXXXX"</td><td>昵称</td></tr>
     *   <tr><td>{@code language}</td><td>默认 "zh_CN"</td><td>语言偏好</td></tr>
     *   <tr><td>{@code onboardingStatus}</td><td>"ONBOARDING_PENDING"</td><td>引导状态</td></tr>
     *   <tr><td>{@code avatarUrl}</td><td>{@code payload.avatarUrl}（微信接口拉取）</td><td>微信头像</td></tr>
     *   <tr><td>{@code country}</td><td>{@code payload.country}（微信接口拉取）</td><td>国家</td></tr>
     *   <tr><td>{@code province}</td><td>{@code payload.province}（微信接口拉取）</td><td>省份</td></tr>
     *   <tr><td>{@code city}</td><td>{@code payload.city}（微信接口拉取）</td><td>城市</td></tr>
     *   <tr><td>{@code accessToken} 等</td><td>{@code payload.wechatToken}（若存在）</td><td>微信授权 Token</td></tr>
     * </table>
     *
     * @param user    用户实体（新创建或从 DB 加载的空壳用户）
     * @param payload 注册票据载荷
     * @param request 注册请求
     * @see #newRegisteredUser(AuthTicketPayload, RegisterRequest)
     */
    private void applyRegistration(UserEntity user, AuthTicketPayload payload, RegisterRequest request) {
        user.setPhone(payload.getPhone());
        user.setUnionId(payload.getUnionId());
        user.setGender(request.getGender());
        user.setBirthday(request.getBirthday());
        user.setNickName(normalizeNickname(request.getNickName()));
        user.setLanguage("zh_CN");
        user.setOnboardingStatus("ONBOARDING_PENDING");
        if (payload.getWechatAccessToken() != null) {
            user.updateToken(
                    payload.getWechatAccessToken(),
                    payload.getWechatRefreshToken(),
                    payload.getWechatTokenExpireTime());
        }
        // 应用票据中携带的微信用户资料（头像、地区等），用户注册时填写的性别优先
        if (payload.getAvatarUrl() != null && !payload.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(payload.getAvatarUrl());
        }
        if (payload.getCountry() != null && !payload.getCountry().isBlank()) {
            user.setCountry(payload.getCountry());
        }
        if (payload.getProvince() != null && !payload.getProvince().isBlank()) {
            user.setProvince(payload.getProvince());
        }
        if (payload.getCity() != null && !payload.getCity().isBlank()) {
            user.setCity(payload.getCity());
        }
        if (payload.getLanguage() != null && !payload.getLanguage().isBlank()) {
            user.setLanguage(payload.getLanguage());
        }
    }

    /**
     * 生成 JWT Token 并构建标准登录响应。
     *
     * <p>通过 openId 和 deviceId 生成双 Token（accessToken + refreshToken），
     * 将 Token 与用户信息以 JSON 格式写入 Redis（由
     * {@link TokenService#storeToken(String, String, String, com.apex.scaffold.common.dto.TokenStoreEntry, com.apex.scaffold.common.dto.TokenStoreEntry)}
     * 保证幂等），最后通过 {@link LoginResponseHelper#build(UserEntity, String, String, boolean, String, String)}
     * 组装含用户信息的登录响应。</p>
     *
     * @param user     用户实体
     * @param deviceId 设备标识（用于 Token 绑定设备）
     * @param clientIp 客户端 IP
     * @param newUser  是否为新用户（影响 {@code isNewUser} 标记）
     * @return 含 accessToken / refreshToken 和用户信息的登录响应
     */
    private Mono<LoginResponse> buildLoginResponse(UserEntity user, String deviceId, String clientIp, boolean newUser) {
        String subject = user.getOpenId();
        String accessToken = tokenService.generateAccessToken(subject, deviceId);
        String refreshToken = tokenService.generateRefreshToken(subject, deviceId);
        TokenStoreEntry tokenEntry = buildTokenEntry(user);
        // 记录首次登录信息（lastLoginTime / lastLoginIp），与 AbstractPhoneLoginStrategy 保持一致
        return userService
                .updateLoginInfo(user.getId(), clientIp)
                .then(tokenService.storeToken(subject, accessToken, refreshToken, tokenEntry, tokenEntry))
                .then(Mono.fromSupplier(() -> loginResponseHelper.build(
                        user, accessToken, refreshToken, newUser, user.getPhone(), clientIp)));
    }

    /**
     * 从用户实体构建 Token 存储条目。
     *
     * @param user 用户实体
     * @return 包含用户基础信息的 TokenStoreEntry
     */
    private TokenStoreEntry buildTokenEntry(UserEntity user) {
        return TokenStoreEntry.builder()
                .userId(user.getId())
                .openId(user.getOpenId())
                .nickName(user.getNickName())
                .avatarUrl(user.getAvatarUrl())
                .gender(user.getGender())
                .phone(user.getPhone())
                .unionId(user.getUnionId())
                .build();
    }

    /**
     * 校验注册表单中的性别和生日字段。
     *
     * <h3>校验规则</h3>
     * <ul>
     *   <li>{@code gender} — 必须为 1（男）或 2（女），不可为 {@code null}</li>
     *   <li>{@code birthday} — 必须不为 {@code null} 且严格早于当前日期（不允许等于或晚于今天）</li>
     * </ul>
     *
     * @param request 注册请求
     * @throws BusinessException 性别或生日不合法（code={@link ResultCode#PARAM_ERROR}）
     */
    private void validateRegistrationProfile(RegisterRequest request) {
        LocalDate birthday = request.getBirthday();
        if (request.getGender() == null
                || (request.getGender() != 1 && request.getGender() != 2)
                || birthday == null
                || !birthday.isBefore(LocalDate.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "性别或生日不合法");
        }
    }

    /**
     * 规范化昵称。
     *
     * <p>若用户传入了非空昵称，去除首尾空白后返回；否则自动生成
     * "{@code Apex用户XXXXXXXX}" 格式的随机昵称（8 位数字）。</p>
     *
     * @param nickname 用户输入的原始昵称，可能为 {@code null} 或空字符串
     * @return 规范化后的昵称
     */
    private String normalizeNickname(String nickname) {
        if (nickname != null && !nickname.isBlank()) {
            return nickname.trim();
        }
        return "Apex用户" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    /**
     * 判断 openId 是否为占位符（非真实微信 openId）。
     *
     * <h3>占位符规则</h3>
     * <p>满足以下任一条件即为占位符：</p>
     * <ul>
     *   <li>{@code null}</li>
     *   <li>空白字符串</li>
     *   <li>以 {@code "PHONE_"} 开头（独立手机号注册用户）</li>
     * </ul>
     *
     * @param openId 用户 openId
     * @return 是占位符时返回 {@code true}
     */
    private boolean isPlaceholderOpenId(String openId) {
        return openId == null || openId.isBlank() || openId.startsWith("PHONE_");
    }

    /**
     * 判断 openId 是否为真实微信 openId（非占位符）。
     *
     * <p>与 {@link #isPlaceholderOpenId(String)} 逻辑互斥。</p>
     *
     * @param openId 用户 openId
     * @return 是真实微信 openId 时返回 {@code true}
     * @see #isPlaceholderOpenId(String)
     */
    private boolean isRealWechatOpenId(String openId) {
        return !isPlaceholderOpenId(openId);
    }

    /**
     * 判断票据中的 openId 与已有用户是否属于同一注册身份。
     *
     * <p>票据未携带 openId（如纯手机号注册）时视为同一身份。</p>
     *
     * @param existing 已存在的用户
     * @param payload  注册票据载荷
     * @return 身份一致时返回 {@code true}
     */
    private boolean isSameRegistrationIdentity(UserEntity existing, AuthTicketPayload payload) {
        if (payload.getOpenId() == null || payload.getOpenId().isBlank()) {
            return true;
        }
        return payload.getOpenId().equals(existing.getOpenId());
    }
}
