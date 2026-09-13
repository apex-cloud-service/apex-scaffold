package com.apex.scaffold.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.apex.scaffold.application.assembler.UserAssembler;
import com.apex.scaffold.application.config.LoginProtectionProperties;
import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.dto.MobileValidateResponse;
import com.apex.scaffold.application.dto.SendSmsRequest;
import com.apex.scaffold.application.dto.WxPhoneRequest;
import com.apex.scaffold.application.security.CaptchaService;
import com.apex.scaffold.application.service.login.LoginStrategyProvider;
import com.apex.scaffold.common.dto.TokenStoreEntry;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.SensitiveDataUtil;
import com.apex.scaffold.domain.gateway.ChuanglanGateway;
import com.apex.scaffold.domain.gateway.WeChatGateway;
import com.apex.scaffold.domain.service.TokenBlacklistService;
import com.apex.scaffold.domain.service.TokenService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 认证服务应用类
 *
 * <p>
 * 处理微信 APP 登录、Token 生成与刷新、退出登录等核心功能。
 * 作为应用层服务，协调领域层基础设施完成认证流程。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>微信 APP 登录：支持验证码登录、一键登录和短信验证码登录，使用策略模式委派具体策略</li>
 *   <li>发送短信验证码：用于短信验证码登录场景，支持普通短信和创蓝短信平台</li>
 *   <li>Token 刷新：使用 refreshToken 换取新的 accessToken</li>
 *   <li>退出登录：使当前 Token 失效并清理相关数据</li>
 *   <li>手机号绑定：通过微信授权获取并绑定用户手机号</li>
 *   <li>登录暴力破解防护：基于 Redis 的 IP 维度和用户维度的失败次数限制</li>
 * </ul>
 *
 * <h2>设计模式</h2>
 * <ul>
 *   <li>策略模式：登录方式通过 {@link com.apex.scaffold.application.service.login.LoginStrategy} 接口实现，
 *   由 {@link LoginStrategyProvider} 根据请求中的登录类型动态选择合适的策略</li>
 * </ul>
 *
 * <h2>安全特性</h2>
 * <ul>
 *   <li>集成 Sentinel 限流保护登录和刷新接口</li>
 *   <li>Token 黑名单机制支持主动失效</li>
 *   <li>登录暴力破解防护：基于 Redis 的 IP 和用户维度失败次数限制</li>
 * </ul>
 *
 * @author Apex Team
 * @version 3.0.0
 * @since 1.0.0
 * @see TokenService
 * @see TokenBlacklistService
 * @see LoginStrategyProvider
 */
@Slf4j
@Service
/**n * AuthServicen *n * <p>TODO: 补充服务的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class AuthService {

    /** Token 类型标识 */
    private static final String TOKEN_TYPE = "Bearer";
    /** Token黑名单TTL（毫秒） */
    private static final long TOKEN_BLACKLIST_TTL_MS = 7200000L;

    /** AccessToken 过期时间（毫秒），默认 2 小时。expiresIn 由此值 / 1000 计算。 */
    @Value("${jwt.access-token-expire:7200000}")
    private long accessTokenExpireMs;

    /** Redis 登录失败 IP 计数器键前缀 */
    private static final String LOGIN_FAIL_IP_PREFIX = "login:fail:ip:";

    /**
     * Redis 登录失败用户名计数器键前缀。
     *
     * <p>与用户名组合构成 Redis key，用于记录指定用户的登录失败次数。</p>
     */
    private static final String LOGIN_FAIL_USERNAME_PREFIX = "login:fail:username:";

    /** 登录保护被关闭时的告警只输出一次，避免刷屏（配置诚实性）。 */
    private final java.util.concurrent.atomic.AtomicBoolean loginProtectionDisabledLogged =
            new java.util.concurrent.atomic.AtomicBoolean();

    /** Token 服务，处理 Token 生成、验证和管理 */
    private final TokenService tokenService;

    /** Token 黑名单服务，用于 Token 主动失效 */
    private final TokenBlacklistService tokenBlacklistService;

    /** 用户服务，处理用户相关操作 */
    private final UserService userService;

    /** 登录策略提供者，根据登录类型委派不同策略 */
    private final LoginStrategyProvider loginStrategyProvider;

    /** 微信网关，封装微信 API 调用 */
    private final WeChatGateway weChatGateway;

    /** 创蓝网关，封装创蓝闪验 API 调用 */
    private final ChuanglanGateway chuanglanGateway;

    /** 短信验证码发送协调器，负责手机号限流和真实供应商调用。 */
    private final SmsVerificationCoordinator smsVerificationCoordinator;

    /** IO 密集型任务线程池 */
    private final ExecutorService ioTaskExecutor;

    /**
     * Redis 响应式字符串模板。
     *
     * <p>用于登录失败计数器的原子递增和过期设置。</p>
     */
    private final ReactiveStringRedisTemplate redisTemplate;

    /** 登录保护配置属性，包含失败次数阈值与时间窗口。 */
    private final LoginProtectionProperties loginProtectionProperties;

    /** 图形验证码服务，用于登录防自动化爆破校验。 */
    private final CaptchaService captchaService;

    /** 登录日志服务，用于记录每次登录尝试（成功/失败）。 */
    private final LoginLogService loginLogService;

    /** 用户组装器，用于领域对象与DTO转换 */
    private final UserAssembler userAssembler;

    /**
     * 构造方法注入依赖
     *
     * @param tokenService Token 服务
     * @param tokenBlacklistService Token 黑名单服务
     * @param userService 用户服务
     * @param loginStrategyProvider 登录策略提供者
     * @param weChatGateway 微信网关
     * @param chuanglanGateway 创蓝网关
     * @param smsVerificationCoordinator 短信验证码发送协调器
     * @param ioTaskExecutor IO 密集型任务线程池
     * @param redisTemplate Redis 响应式字符串模板
     * @param loginProtectionProperties 登录保护配置
     * @param captchaService 图形验证码服务
     * @param loginLogService 登录日志服务
     * @param userAssembler 用户组装器
     */
    public AuthService(
            TokenService tokenService,
            TokenBlacklistService tokenBlacklistService,
            UserService userService,
            LoginStrategyProvider loginStrategyProvider,
            WeChatGateway weChatGateway,
            ChuanglanGateway chuanglanGateway,
            SmsVerificationCoordinator smsVerificationCoordinator,
            ExecutorService ioTaskExecutor,
            ReactiveStringRedisTemplate redisTemplate,
            LoginProtectionProperties loginProtectionProperties,
            CaptchaService captchaService,
            LoginLogService loginLogService,
            UserAssembler userAssembler) {
        this.tokenService = tokenService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.userService = userService;
        this.loginStrategyProvider = loginStrategyProvider;
        this.weChatGateway = weChatGateway;
        this.chuanglanGateway = chuanglanGateway;
        this.smsVerificationCoordinator = smsVerificationCoordinator;
        this.ioTaskExecutor = ioTaskExecutor;
        this.redisTemplate = redisTemplate;
        this.loginProtectionProperties = loginProtectionProperties;
        this.captchaService = captchaService;
        this.loginLogService = loginLogService;
        this.userAssembler = userAssembler;
    }

    /**
     * 微信 APP 登录（带客户端 IP 的暴力破解防护版本）
     *
     * <p>
     * 在原有登录逻辑基础上增加暴力破解防护：
     * <ul>
     *   <li>登录前检查客户端 IP 的失败次数，超过阈值则拒绝</li>
     *   <li>登录前检查用户名的失败次数（手机号维度），超过阈值则拒绝</li>
     *   <li>登录成功后清除 IP 和用户名的失败计数</li>
     *   <li>登录失败后增加 IP 和用户名的失败计数</li>
     * </ul>
     *
     * @param request 登录请求参数，包含登录类型和对应凭证
     * @param clientIp 客户端 IP 地址，用于暴力破解防护计数
     * @return 登录响应信息，包含 Token 对及用户信息
     * @throws BusinessException 当登录策略不支持、认证失败或触发暴力破解防护时抛出
     */
    @SentinelResource(value = "authLogin", blockHandler = "handleLoginBlock", fallback = "loginFallback")
    public Mono<LoginResponse> login(LoginRequest request, String clientIp) {
        Mono<Void> preChecks;
        if (Boolean.FALSE.equals(loginProtectionProperties.getEnabled())) {
            // 配置诚实性：enabled=false 时真实跳过防护并告警一次，避免「配置写了但没生效」
            if (loginProtectionDisabledLogged.compareAndSet(false, true)) {
                log.warn("登录保护已关闭（apex.security.login-protection.enabled=false），" + "跳过暴力破解检查与验证码升级，生产环境存在撞库/爆破风险");
            }
            preChecks = Mono.empty();
        } else {
            preChecks = verifyCaptchaIfPresent(request, clientIp).then(checkBruteForce(request, clientIp));
        }
        return preChecks.then(Mono.defer(() -> loginStrategyProvider
                .getStrategy(request.getLoginType())
                .login(request)
                .flatMap(response -> recordLoginSuccess(request, clientIp)
                        .then(Mono.defer(() -> refreshHeartbeat(response)))
                        .thenReturn(response))
                .onErrorResume(e -> recordLoginFailure(request, clientIp).then(Mono.error(e)))));
    }

    /**
     * 登录成功后刷新用户最后心跳时间（在线判定）。
     *
     * <p>基于登录响应中的 {@code openId} 查询用户并更新
     * {@code last_heartbeat_time}，支撑「在线用户列表」按最近
     * 5 分钟窗口判定在线状态。新用户（openId 为空）跳过。</p>
     *
     * @param response 登录响应
     * @return 心跳更新完成信号
     * @since 2.2.0
     */
    private Mono<Void> refreshHeartbeat(LoginResponse response) {
        if (response == null
                || response.getOpenId() == null
                || response.getOpenId().isBlank()) {
            return Mono.empty();
        }
        Mono<com.apex.scaffold.domain.model.entity.UserEntity> userMono = userService.getByOpenId(response.getOpenId());
        if (userMono == null) {
            return Mono.empty();
        }
        // 心跳刷新为旁路逻辑：任何异常（含 mock/降级场景）都不应影响登录成功
        return userMono.flatMap(user -> userService.updateLastHeartbeat(user.getId()))
                .onErrorResume(e -> Mono.empty())
                .switchIfEmpty(Mono.empty());
    }

    /**
     * 登录前校验图形验证码（可选 + 失败升级强制）。
     *
     * <p>两种触发路径：</p>
     * <ul>
     *   <li><b>客户端主动携带</b>：请求带 {@code captchaId} 时校验，失败抛
     *       {@code CAPTCHA_INVALID}</li>
     *   <li><b>升级强制</b>：客户端 IP 在窗口内的失败次数达到
     *       {@code apex.security.login-protection.captcha-after-failures}（默认 3 次）后，
     *       登录必须携带验证码，否则抛 {@code CAPTCHA_REQUIRED}——在 IP 锁定（默认 20 次）
     *       之前引入人机校验拦截层，缓解同 IP 撞库</li>
     * </ul>
     *
     * <p>升级阈值未配置（{@code null}）或非正数时退化为「仅客户端主动携带时校验」。</p>
     *
     * @param request 登录请求
     * @param clientIp 客户端 IP，用于查询失败计数
     * @return 校验通过的信号
     * @throws BusinessException 当验证码校验失败或升级强制未携带验证码时抛出
     */
    private Mono<Void> verifyCaptchaIfPresent(LoginRequest request, String clientIp) {
        Integer captchaAfterFailures = loginProtectionProperties.getCaptchaAfterFailures();
        if (captchaAfterFailures == null || captchaAfterFailures <= 0) {
            return verifyIfPresent(request);
        }
        String ipKey = LOGIN_FAIL_IP_PREFIX + clientIp;
        return redisTemplate.opsForValue().get(ipKey).defaultIfEmpty("0").flatMap(count -> {
            int failCount = parseFailCount(count);
            if (failCount >= captchaAfterFailures) {
                if (request.getCaptchaId() == null || request.getCaptchaId().isBlank()) {
                    log.warn("登录验证码升级强制，IP: {}, failCount: {}, 缺少验证码", clientIp, failCount);
                    return Mono.error(new BusinessException(ResultCode.CAPTCHA_REQUIRED));
                }
                return verifyCaptcha(request);
            }
            return verifyIfPresent(request);
        });
    }

    /**
     * 校验请求携带的图形验证码。
     *
     * @param request 登录请求
     * @return 校验通过的信号
     * @throws BusinessException 验证码无效时抛出 {@code CAPTCHA_INVALID}
     */
    private Mono<Void> verifyCaptcha(LoginRequest request) {
        return captchaService
                .verify(request.getCaptchaId(), request.getCaptchaCode())
                .flatMap(valid -> valid ? Mono.empty() : Mono.error(new BusinessException(ResultCode.CAPTCHA_INVALID)));
    }

    /**
     * 仅当请求携带 {@code captchaId} 时校验图形验证码。
     *
     * <p>APP 客户端未开启验证码时不传该字段，直接放行。</p>
     *
     * @param request 登录请求
     * @return 校验通过的信号
     * @throws BusinessException 当验证码校验失败时抛出
     */
    private Mono<Void> verifyIfPresent(LoginRequest request) {
        if (request.getCaptchaId() == null || request.getCaptchaId().isBlank()) {
            return Mono.empty();
        }
        return verifyCaptcha(request);
    }

    /**
     * 将 Redis 中的失败计数字符串解析为整数，格式异常时视为 0。
     *
     * @param count 计数字符串
     * @return 解析后的失败次数
     */
    private int parseFailCount(String count) {
        try {
            return Integer.parseInt(count);
        } catch (NumberFormatException e) {
            log.warn("登录失败计数格式异常: {}", count);
            return 0;
        }
    }

    /**
     * 微信 APP 登录（无客户端 IP 版本，不进行暴力破解防护）
     *
     * <p>
     * 保留无参版本以保证向后兼容性。此版本不进行暴力破解防护检测。
     *
     * @param request 登录请求参数，包含登录类型和对应凭证
     * @return 登录响应信息，包含 Token 对及用户信息
     * @throws BusinessException 当登录策略不支持或认证失败时抛出
     */
    @SentinelResource(value = "authLogin", blockHandler = "handleLoginBlock", fallback = "loginFallback")
    public Mono<LoginResponse> login(LoginRequest request) {
        return loginStrategyProvider.getStrategy(request.getLoginType()).login(request);
    }

    /**
     * 检查暴力破解防护
     *
     * <p>
     * 基于 Redis 计数器检查客户端 IP 和用户名的登录失败次数是否超过阈值。
     * 任意一个维度超过阈值则抛出 {@link BusinessException}。
     *
     * @param request 登录请求
     * @param clientIp 客户端 IP
     * @return 校验通过的空 Mono
     * @throws BusinessException 超过失败次数限制时抛出
     */
    private Mono<Void> checkBruteForce(LoginRequest request, String clientIp) {
        String ipKey = LOGIN_FAIL_IP_PREFIX + clientIp;
        String username = resolveUsername(request);

        Mono<Void> ipCheck = redisTemplate
                .opsForValue()
                .get(ipKey)
                .flatMap(count -> {
                    try {
                        int failCount = Integer.parseInt(count);
                        if (failCount >= loginProtectionProperties.getIpMaxAttempts()) {
                            log.warn("IP登录失败次数过多, ip: {}, count: {}", clientIp, failCount);
                            return Mono.error(new BusinessException(ResultCode.LOGIN_LOCKED));
                        }
                    } catch (NumberFormatException e) {
                        log.warn("IP登录失败计数格式异常, ip: {}, count: {}", clientIp, count);
                    }
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.empty())
                .then();

        if (username == null || username.isBlank()) {
            return ipCheck;
        }

        String usernameKey = LOGIN_FAIL_USERNAME_PREFIX + username;
        Mono<Void> usernameCheck = redisTemplate
                .opsForValue()
                .get(usernameKey)
                .flatMap(count -> {
                    try {
                        int failCount = Integer.parseInt(count);
                        if (failCount >= loginProtectionProperties.getUsernameMaxAttempts()) {
                            log.warn("用户名登录失败次数过多, username: {}, count: {}", username, failCount);
                            return Mono.error(new BusinessException(ResultCode.LOGIN_LOCKED));
                        }
                    } catch (NumberFormatException e) {
                        log.warn("用户名登录失败计数格式异常, username: {}, count: {}", username, count);
                    }
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.empty())
                .then();

        return ipCheck.then(usernameCheck);
    }

    /**
     * 记录登录成功
     *
     * <p>登录成功后清除 IP 和用户名的失败计数。
     *
     * @param request 登录请求
     * @param clientIp 客户端 IP
     * @return 空 Mono
     */
    private Mono<Void> recordLoginSuccess(LoginRequest request, String clientIp) {
        String username = resolveUsername(request);
        Mono<Void> cleanup;
        if (Boolean.FALSE.equals(loginProtectionProperties.getEnabled())) {
            // 配置诚实性：登录保护关闭时失败计数从未写入，跳过 Redis 清理，仅记录登录日志。
            cleanup = Mono.empty();
        } else {
            String ipKey = LOGIN_FAIL_IP_PREFIX + clientIp;
            Mono<Void> deleteIp = redisTemplate.opsForValue().delete(ipKey).then();
            cleanup = username == null || username.isBlank()
                    ? deleteIp
                    : deleteIp.then(redisTemplate
                            .opsForValue()
                            .delete(LOGIN_FAIL_USERNAME_PREFIX + username)
                            .then());
        }
        return cleanup.then(safeRecordLoginLog(username, clientIp, true, "登录成功"));
    }

    /**
     * 记录登录失败
     *
     * <p>递增 IP 和用户名的失败计数，并设置过期时间。
     *
     * @param request 登录请求
     * @param clientIp 客户端 IP
     * @return 空 Mono
     */
    private Mono<Void> recordLoginFailure(LoginRequest request, String clientIp) {
        String username = resolveUsername(request);
        Mono<Void> counters;
        if (Boolean.FALSE.equals(loginProtectionProperties.getEnabled())) {
            // 配置诚实性：登录保护关闭时失败计数无实际用途，跳过递增，仅记录登录日志。
            counters = Mono.empty();
        } else {
            String ipKey = LOGIN_FAIL_IP_PREFIX + clientIp;
            Duration windowSize = Duration.ofSeconds(loginProtectionProperties.getWindowSize());
            Mono<Void> incrementIp = redisTemplate
                    .opsForValue()
                    .increment(ipKey)
                    .flatMap(count -> {
                        if (count == 1) {
                            return redisTemplate.expire(ipKey, windowSize).then();
                        }
                        return Mono.empty();
                    })
                    .then();
            counters = username == null || username.isBlank()
                    ? incrementIp
                    : incrementIp.then(incrementLoginFailCount(LOGIN_FAIL_USERNAME_PREFIX + username, windowSize));
        }
        return counters.then(safeRecordLoginLog(username, clientIp, false, "登录失败"));
    }

    /**
     * 递增登录失败计数并设置过期时间。
     *
     * @param key 计数器 Redis 键
     * @param windowSize 计数窗口
     * @return 空 Mono
     */
    private Mono<Void> incrementLoginFailCount(String key, Duration windowSize) {
        return redisTemplate
                .opsForValue()
                .increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(key, windowSize).then();
                    }
                    return Mono.empty();
                })
                .then();
    }

    /**
     * 记录登录日志（失败不阻塞主流程）。
     *
     * <p>登录日志写入失败仅记录 WARN 日志，不向登录流程传播异常，
     * 保证审计能力不影响登录可用性。</p>
     *
     * @param username 登录账号
     * @param clientIp 客户端 IP
     * @param success 是否成功
     * @param message 登录消息
     * @return 空 Mono
     */
    private Mono<Void> safeRecordLoginLog(String username, String clientIp, boolean success, String message) {
        if (loginLogService == null) {
            return Mono.empty();
        }
        com.apex.scaffold.domain.model.entity.LoginLogEntity entity =
                com.apex.scaffold.domain.model.entity.LoginLogEntity.create(
                        username, clientIp, resolveBrowser(), resolveOs(), success, message);
        return loginLogService.record(entity).onErrorResume(error -> {
            log.warn("登录日志记录失败，username: {}, error: {}", username, error.getMessage());
            return Mono.empty();
        });
    }

    /**
     * 解析浏览器信息（占位实现，可接入 UA 解析库）。
     *
     * @return 浏览器标识，当前返回固定值
     */
    private String resolveBrowser() {
        return "unknown";
    }

    /**
     * 解析操作系统信息（占位实现，可接入 UA 解析库）。
     *
     * @return 操作系统标识，当前返回固定值
     */
    private String resolveOs() {
        return "unknown";
    }

    /**
     * 从登录请求中解析用户名标识
     *
     * <p>优先使用手机号作为用户名标识，适用于短信验证码登录场景。
     * 若手机号为空，则尝试使用微信授权码作为标识。
     *
     * @param request 登录请求
     * @return 用户名标识，可能为 null
     */
    private String resolveUsername(LoginRequest request) {
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            return request.getPhone();
        }
        if (request.getCode() != null && !request.getCode().isBlank()) {
            return request.getCode();
        }
        return null;
    }

    /**
     * 刷新 Token（含设备指纹校验及用户信息补全）。
     *
     * <p>
     * 使用 refreshToken 换取新的 accessToken，同时延长 refreshToken 有效期。
     * 校验 refreshToken 中的设备指纹与请求设备一致，防止 Token 被跨设备盗用。
     * 刷新成功后查询用户信息，将包含性别在内的用户基础信息一并返回，
     * 同时将用户信息随新 Token 写入 Redis，避免鉴权时重复查库。
     *
     * <h3>Token 过期异常细化处理</h3>
     * <p>对 RefreshToken 过期的异常进行细化处理：</p>
     * <ul>
     *   <li>{@link ExpiredJwtException}（Token 已过期）→ 返回
     *       {@link ResultCode#TOKEN_EXPIRED}(40101)，前端可据此精确识别
     *       "需重新登录"场景并引导用户跳转登录页</li>
     *   <li>其他 {@link JwtException}（签名无效、格式错误等）→ 返回
     *       {@link ResultCode#UNAUTHORIZED}(401)，前端提示"登录状态异常"</li>
     * </ul>
     * <p>通过将 RefreshToken 过期的异常与通用未授权异常分离，确保在整个 Token
     * 生命周期链路中，前端始终能区分"需要重新登录"和"请求异常"两种场景，
     * 避免用户长时间不操作后看到模糊的"未授权"提示而无法正确跳转至登录页面。</p>
     *
     * <h3>异常处理链路</h3>
     * <table>
     *   <tr><th>异常类型</th><th>含义</th><th>ResultCode</th><th>前端行为</th></tr>
     *   <tr><td>{@link ExpiredJwtException}</td><td>RefreshToken 已过期</td>
     *       <td>{@link ResultCode#TOKEN_EXPIRED}(40101)</td>
     *       <td>清除本地 Token → 跳转登录页</td></tr>
     *   <tr><td>{@link JwtException}（非过期）</td><td>Token 签名无效/格式错误</td>
     *       <td>{@link ResultCode#UNAUTHORIZED}(401)</td>
     *       <td>提示"登录状态异常，请重新登录"</td></tr>
     *   <tr><td>设备不匹配</td><td>Token 被盗用风险</td>
     *       <td>{@link ResultCode#REFRESH_TOKEN_INVALID}(1009)</td>
     *       <td>提示"Token 无效，请重新登录"</td></tr>
     *   <tr><td>Redis 中不存在</td><td>Token 已被删除或登出</td>
     *       <td>{@link ResultCode#UNAUTHORIZED}(401)</td>
     *       <td>提示"Token 已注销，请重新登录"</td></tr>
     * </table>
     *
     * @param refreshToken 刷新令牌
     * @param deviceId     设备标识，用于校验设备一致性
     * @return 新的登录响应，包含新的 Token 对及用户信息（含性别）
     * @throws BusinessException 当 refreshToken 已失效、设备不匹配或解析失败时抛出
     * @since 1.0.0
     */
    @SentinelResource(value = "authRefresh", blockHandler = "handleRefreshBlock", fallback = "refreshFallback")
    public Mono<LoginResponse> refreshToken(String refreshToken, String deviceId) {
        return Mono.fromCallable(() -> tokenService.parseToken(refreshToken))
                .subscribeOn(Schedulers.fromExecutor(ioTaskExecutor))
                .onErrorResume(ExpiredJwtException.class, e -> {
                    log.warn("RefreshToken 已过期，用户需重新登录");
                    return Mono.error(new BusinessException(ResultCode.TOKEN_EXPIRED, "RefreshToken 已过期，请重新登录"));
                })
                .onErrorResume(JwtException.class, e -> Mono.error(new BusinessException(ResultCode.UNAUTHORIZED)))
                .flatMap(claims -> {
                    String openId = (String) claims.get("sub");

                    // 校验设备指纹一致性
                    String tokenDeviceId = (String) claims.get("deviceId");
                    if (tokenDeviceId != null && !tokenDeviceId.isBlank() && !tokenDeviceId.equals(deviceId)) {
                        log.warn(
                                "Token刷新设备不匹配, openId: {}, tokenDeviceId: {}, requestDeviceId: {}",
                                openId,
                                tokenDeviceId,
                                deviceId);
                        return Mono.error(new BusinessException(ResultCode.REFRESH_TOKEN_INVALID));
                    }

                    java.util.Date issuedAt = claims.get("iat") instanceof java.util.Date date ? date : null;
                    Mono<Boolean> subjectRevoked = tokenBlacklistService.isSubjectRevoked(openId, issuedAt);
                    Mono<Boolean> safeSubjectRevoked = subjectRevoked == null ? Mono.empty() : subjectRevoked;
                    return safeSubjectRevoked.defaultIfEmpty(false).flatMap(revoked -> {
                        if (Boolean.TRUE.equals(revoked)) {
                            return Mono.<LoginResponse>error(new BusinessException(ResultCode.UNAUTHORIZED));
                        }

                        // 账号注销后必须先确认当前身份仍存在，再生成新令牌。
                        // 不再为已删除用户走“无用户信息”的兼容分支，避免旧 refresh token
                        // 在同一 openId 重新注册后复活旧会话。
                        return userService
                                .getByOpenId(openId)
                                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED)))
                                .flatMap(user -> {
                                    // 先生成新令牌对（无需锁），再通过 rotateRefreshToken 在分布式锁内
                                    // 原子完成旧令牌比对与新令牌写入。
                                    String newAccessToken = tokenService.generateAccessToken(openId, deviceId);
                                    String newRefreshToken = tokenService.generateRefreshToken(openId, deviceId);
                                    return rotateAndRespond(
                                            openId,
                                            refreshToken,
                                            newAccessToken,
                                            newRefreshToken,
                                            buildTokenEntry(user),
                                            buildTokenEntry(user),
                                            user);
                                });
                    });
                })
                .doOnError(this::logRefreshTokenFailure);
    }

    /**
     * 记录刷新 Token 失败日志。
     *
     * <p>业务拒绝（如旧刷新令牌已被消费、令牌不匹配导致 401 重新登录）属于预期流程，
     * 使用 WARN 级别且不打印堆栈，避免把客户端正常操作误报为服务端 ERROR；
     * 仅真正的系统异常（如 Redis 故障）保留 ERROR 级别与堆栈。</p>
     *
     * @param error 刷新过程中的异常
     */
    private void logRefreshTokenFailure(Throwable error) {
        if (error instanceof BusinessException) {
            log.warn("刷新Token被业务拒绝: {}", error.getMessage());
        } else {
            log.error("刷新Token失败: {}", error.getMessage(), error);
        }
    }

    /**
     * 在分布式锁内轮换刷新令牌，并组装刷新响应。
     *
     * <p>轮换成功时返回包含新令牌对与用户信息的登录响应；轮换失败（旧令牌已被消费、
     * 不匹配或获取锁超时）时返回 401，提示客户端重新登录。用户信息不存在时
     * （{@code user} 为 {@code null}）返回不含用户信息的响应，保持与旧流程一致。</p>
     *
     * @param openId          用户标识（OpenID）
     * @param oldRefreshToken 待轮换的旧刷新令牌
     * @param newAccessToken  新生成的 AccessToken
     * @param newRefreshToken 新生成的 RefreshToken
     * @param accessEntry     AccessToken 对应的用户信息条目，可为 null
     * @param refreshEntry    RefreshToken 对应的用户信息条目，可为 null
     * @param user            用户实体，可为 null
     * @return 刷新成功后的登录响应
     */
    private Mono<LoginResponse> rotateAndRespond(
            String openId,
            String oldRefreshToken,
            String newAccessToken,
            String newRefreshToken,
            TokenStoreEntry accessEntry,
            TokenStoreEntry refreshEntry,
            com.apex.scaffold.domain.model.entity.UserEntity user) {
        return tokenService
                .rotateRefreshToken(openId, oldRefreshToken, newAccessToken, newRefreshToken, accessEntry, refreshEntry)
                .flatMap(rotated -> {
                    if (!Boolean.TRUE.equals(rotated)) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED));
                    }
                    if (user != null) {
                        return Mono.fromSupplier(
                                () -> buildLoginResponse(newAccessToken, newRefreshToken, openId, user));
                    }
                    LoginResponse response = new LoginResponse();
                    response.setAccessToken(newAccessToken);
                    response.setRefreshToken(newRefreshToken);
                    response.setTokenType(TOKEN_TYPE);
                    response.setExpiresIn(accessTokenExpireMs / 1000L);
                    response.setOpenId(openId);
                    return Mono.just(response);
                });
    }

    /**
     * 从用户实体构建 Token 存储条目。
     *
     * @param user 用户实体
     * @return 包含用户基础信息的 TokenStoreEntry
     */
    private TokenStoreEntry buildTokenEntry(com.apex.scaffold.domain.model.entity.UserEntity user) {
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
     * 构建登录响应对象（含用户信息）。
     *
     * @param newAccessToken  新 AccessToken
     * @param newRefreshToken 新 RefreshToken
     * @param openId          用户 OpenID
     * @param user            用户实体
     * @return LoginResponse
     */
    private LoginResponse buildLoginResponse(
            String newAccessToken,
            String newRefreshToken,
            String openId,
            com.apex.scaffold.domain.model.entity.UserEntity user) {
        LoginResponse response = userAssembler.toLoginResponse(user);
        response.setAccessToken(newAccessToken);
        response.setRefreshToken(newRefreshToken);
        response.setTokenType(TOKEN_TYPE);
        response.setExpiresIn(accessTokenExpireMs / 1000L);
        return response;
    }

    /**
     * 退出登录
     *
     * <p>
     * 使当前用户的所有 Token 失效，包括：
     * <ul>
     *   <li>将 Token 加入黑名单</li>
     *   <li>删除存储的 Token</li>
     * </ul>
     *
     * @param token 当前访问令牌
     * @param openId 当前用户 OpenID
     * @return 空响应，表示退出成功
     */
    public Mono<Void> logout(String token, String openId) {
        log.info("用户退出登录, openId: {}", openId);
        return Mono.fromCallable(() -> tokenService.parseToken(token))
                .subscribeOn(Schedulers.fromExecutor(ioTaskExecutor))
                .flatMap(claims -> {
                    String tokenOpenId = (String) claims.get("sub");
                    if (tokenOpenId == null || !tokenOpenId.equals(openId)) {
                        return Mono.error(new BusinessException(ResultCode.TOKEN_USER_MISMATCH));
                    }
                    /*
                     * 登出流程的三层纵深防御：
                     * 1. AccessToken 加入黑名单（jti 校验拦截）
                     * 2. RefreshToken 加入黑名单（防止登出后通过 RefreshToken 续期）
                     * 3. 从 Redis 中删除两个 Token 的存储键
                     */
                    return tokenBlacklistService
                            .add(token, TOKEN_BLACKLIST_TTL_MS)
                            .then(tokenService.findRefreshToken(tokenOpenId))
                            .flatMap(storedRefreshToken ->
                                    tokenBlacklistService.add(storedRefreshToken, TOKEN_BLACKLIST_TTL_MS))
                            .then(tokenService.deleteToken(tokenOpenId))
                            .then();
                });
    }

    /**
     * 登录限流降级处理
     *
     * <p>当登录请求触发 Sentinel 限流时调用。直接抛出 {@link BusinessException}，
     * 由全局异常处理器返回统一错误响应。避免返回空 Token 对象导致前端逻辑异常。
     *
     * @param request 原始登录请求
     * @param ex      Sentinel 限流异常
     * @return 不会正常返回，始终抛出异常
     * @throws BusinessException 限流业务异常
     */
    public Mono<LoginResponse> handleLoginBlock(LoginRequest request, BlockException ex) {
        log.warn("登录接口限流，traceId: {}", com.apex.scaffold.common.util.TraceIdUtil.getTraceId());
        return Mono.error(new BusinessException(ResultCode.SYSTEM_BUSY));
    }

    /**
     * 绑定微信手机号
     *
     * <p>
     * 通过微信手机号授权凭证获取用户手机号，并绑定到用户账户。
     * 如果用户是手机号注册用户（openId 为占位格式如 PHONE_xxx），
     * 则绑定后使用真实 openId 替换占位 openId，同时清理旧 Token 以确保安全。
     *
     * @param request 手机号绑定请求，包含微信授权凭证
     * @param openId 当前用户 OpenID（微信返回的真实 openId）
     * @return 包含手机号信息的 Map，key 为 {@code "phone"}
     * @throws BusinessException 当获取手机号失败时抛出
     */
    public Mono<java.util.Map<String, String>> bindPhone(WxPhoneRequest request, String openId) {
        log.info("绑定微信手机号, openId: {}", openId);
        return weChatGateway.getPhoneNumber(request.getPhoneCode()).flatMap(phoneResult -> {
            String phone = phoneResult.getPhoneNumber();
            if (phone == null || phone.isBlank()) {
                return Mono.error(new BusinessException(ResultCode.PHONE_FETCH_FAILED));
            }
            return userService
                    .getByOpenId(openId)
                    .flatMap(user -> {
                        // 手机号注册用户绑定微信: 占位 openId (PHONE_xxx) 替换为真实 openId
                        String oldOpenId = user.getOpenId();
                        boolean isPlaceholder = oldOpenId != null && oldOpenId.startsWith("PHONE_");
                        if (isPlaceholder) {
                            // 清理旧 Token（旧 Token 以占位 openId 为 subject, 需要失效）
                            return tokenService.deleteToken(oldOpenId).then(Mono.defer(() -> {
                                user.setOpenId(openId);
                                user.setPhone(phone);
                                return userService.update(user);
                            }));
                        }
                        user.setPhone(phone);
                        return userService.update(user);
                    })
                    .thenReturn(java.util.Map.of("phone", phone));
        });
    }

    /**
     * 发送短信验证码
     *
     * <p>
     * 向指定手机号发送验证码，用于短信验证码登录场景。
     * 验证码有效期为 5 分钟，同一手机号再次发送会覆盖之前的验证码。
     *
     * @param request 发送短信请求，包含目标手机号
     * @return 空响应，表示发送成功
     */
    @SentinelResource(value = "authSendSms", blockHandler = "handleSendSmsBlock", fallback = "sendSmsFallback")
    public Mono<Void> sendSmsCode(SendSmsRequest request) {
        log.info("发送短信验证码, phone: {}", SensitiveDataUtil.maskPhone(request.getPhone()));
        return smsVerificationCoordinator.send(request.getPhone());
    }

    /**
     * 发送创蓝短信验证码
     *
     * <p>
     * 通过创蓝 253 短信平台向指定手机号发送验证码，用于创蓝短信验证码登录场景。
     * 验证码有效期为 5 分钟，同一手机号再次发送会覆盖之前的验证码。
     *
     * @param request 发送短信请求，包含目标手机号
     * @return 空响应，表示发送成功
     */
    @SentinelResource(value = "authSendClSms", blockHandler = "handleSendSmsBlock", fallback = "sendSmsFallback")
    public Mono<Void> sendClSmsCode(SendSmsRequest request) {
        log.info("发送创蓝短信验证码, phone: {}", SensitiveDataUtil.maskPhone(request.getPhone()));
        return smsVerificationCoordinator.send(request.getPhone());
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
     * <h3>接口规范（创蓝闪验服务端文档V3.X）</h3>
     * <ul>
     *   <li><b>接口地址</b>：https://wsflash.253.com/open/flashsdk/mobile-validate</li>
     *   <li><b>协议</b>：HTTPS</li>
     *   <li><b>请求方式</b>：POST</li>
     *   <li><b>请求内容类型</b>：application/x-www-form-urlencoded</li>
     * </ul>
     *
     * <h3>响应字段说明</h3>
     * <table border="1">
     *   <tr><th>字段名</th><th>类型</th><th>说明</th></tr>
     *   <tr><td>isVerify</td><td>String</td><td>校验结果："1"表示是本机号码，"0"表示非本机号码</td></tr>
     *   <tr><td>tradeNo</td><td>String</td><td>交易流水号</td></tr>
     *   <tr><td>success</td><td>Boolean</td><td>是否校验成功</td></tr>
     * </table>
     *
     * @param token 本机认证SDK返回的临时令牌，一次有效
     * @param mobile 待校验的手机号码
     * @param outId 客户方流水号（可选），用于业务追踪
     * @return 校验结果响应，包含isVerify、tradeNo和success字段
     * @see com.apex.scaffold.domain.gateway.ChuanglanGateway.MobileValidateResult
     * @see <a href="https://doc.chuanglan.com/document/SY88R0I8TCUPR1M3">创蓝闪验SDK服务端接口文档</a>
     */
    public Mono<MobileValidateResponse> validateMobile(String token, String mobile, String outId) {
        log.info(
                "本机号校验, tokenFingerprint: {}, mobile: {}, outId: {}",
                SensitiveDataUtil.fingerprint(token),
                SensitiveDataUtil.maskPhone(mobile),
                outId);
        return chuanglanGateway
                .validateMobile(token, mobile, outId)
                .map(result -> {
                    if (result.isSuccess()) {
                        return MobileValidateResponse.success(result.getIsVerify(), result.getTradeNo());
                    }
                    log.warn(
                            "本机号校验失败, errorCode: {}, errorMessage: {}",
                            result.getErrorCode(),
                            result.getErrorMessage());
                    return MobileValidateResponse.failure();
                })
                .doOnError(e -> {
                    log.error(
                            "本机号校验异常, tokenFingerprint: {}, mobile: {}",
                            SensitiveDataUtil.fingerprint(token),
                            SensitiveDataUtil.maskPhone(mobile),
                            e);
                });
    }

    /**
     * 发送短信限流降级处理
     *
     * @param request 发送短信请求
     * @param ex      Sentinel 限流异常
     * @return 不会正常返回，始终抛出异常
     * @throws BusinessException 限流业务异常
     */
    public Mono<Void> handleSendSmsBlock(SendSmsRequest request, BlockException ex) {
        log.warn("发送短信接口限流, traceId: {}", com.apex.scaffold.common.util.TraceIdUtil.getTraceId());
        return Mono.error(new BusinessException(ResultCode.SMS_RATE_LIMITED));
    }

    /**
     * 发送短信服务降级处理
     *
     * <p>当短信服务调用触发 Sentinel 降级时调用。直接将异常传递或包装后抛出，
     * 由全局异常处理器返回统一错误响应。
     *
     * @param request 发送短信请求
     * @param ex      原始异常，如果是 {@link BusinessException} 则直接传递
     * @return 不会正常返回，始终抛出异常
     * @throws BusinessException 降级业务异常
     */
    public Mono<Void> sendSmsFallback(SendSmsRequest request, Throwable ex) {
        log.error("发送短信服务异常, traceId: {}", com.apex.scaffold.common.util.TraceIdUtil.getTraceId(), ex);
        return Mono.error(new BusinessException(ResultCode.SMS_SERVICE_ERROR));
    }

    /**
     * 登录服务降级处理
     *
     * <p>当登录服务调用触发 Sentinel 降级时调用。如果原始异常已经是
     * {@link BusinessException} 则直接传递，否则包装为通用错误信息抛出。
     *
     * @param request 原始登录请求
     * @param ex      原始异常
     * @return 不会正常返回，始终抛出异常
     * @throws BusinessException 降级业务异常
     */
    public Mono<LoginResponse> loginFallback(LoginRequest request, Throwable ex) {
        log.error("登录服务异常，进入降级逻辑，traceId: {}", com.apex.scaffold.common.util.TraceIdUtil.getTraceId(), ex);
        if (ex instanceof BusinessException) {
            return Mono.error(ex);
        }
        return Mono.error(new BusinessException(ResultCode.LOGIN_SERVICE_ERROR));
    }

    /**
     * 刷新 Token 限流降级处理
     *
     * <p>当刷新 Token 请求触发 Sentinel 限流时调用。
     *
     * @param refreshToken 刷新令牌
     * @param deviceId     设备标识
     * @param ex           Sentinel 限流异常
     * @return 不会正常返回，始终抛出异常
     * @throws BusinessException 限流业务异常
     */
    public Mono<LoginResponse> handleRefreshBlock(String refreshToken, String deviceId, BlockException ex) {
        log.warn("刷新Token 接口限流，traceId: {}", com.apex.scaffold.common.util.TraceIdUtil.getTraceId());
        return Mono.error(new BusinessException(ResultCode.SYSTEM_BUSY));
    }

    /**
     * 刷新 Token 服务降级处理
     *
     * <p>当刷新 Token 服务调用触发 Sentinel 降级时调用。如果原始异常已经是
     * {@link BusinessException} 则直接传递，否则包装为通用错误信息抛出。
     *
     * @param refreshToken 刷新令牌
     * @param deviceId     设备标识
     * @param ex           原始异常
     * @return 不会正常返回，始终抛出异常
     * @throws BusinessException 降级业务异常
     */
    public Mono<LoginResponse> refreshFallback(String refreshToken, String deviceId, Throwable ex) {
        log.error("刷新Token 服务异常，traceId: {}", com.apex.scaffold.common.util.TraceIdUtil.getTraceId(), ex);
        if (ex instanceof BusinessException) {
            return Mono.error(ex);
        }
        return Mono.error(new BusinessException(ResultCode.REFRESH_TOKEN_ERROR));
    }
}
