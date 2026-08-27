package com.apex.scaffold.application.service.login;

import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.PasswordUtil;
import com.apex.scaffold.domain.enums.LoginType;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.service.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 账号密码登录策略（PASSWORD）。
 *
 * <p>实现传统用户名+密码认证，面向管理后台与 B 端账号体系场景。
 * 采用策略模式接入 {@link LoginStrategyProvider}，登录类型 {@link LoginType#PASSWORD}
 * 由 {@code sys_user.username / password} 字段支撑（V3 迁移引入）。</p>
 *
 * <h2>认证流程</h2>
 * <ol>
 *   <li>按 {@code username} 查询用户（{@code uk_username} 唯一索引）</li>
 *   <li>账号不存在 → {@link ResultCode#USER_NOT_FOUND}</li>
 *   <li>账号被禁用（{@code status != 1}）→ {@link ResultCode#LOGIN_FAILED}</li>
 *   <li>BCrypt 密码比对（{@link PasswordUtil}）失败 → {@link ResultCode#LOGIN_FAILED}</li>
 *   <li>通过后签发 JWT Token 对并存储，返回 {@link LoginResponse}（isNewUser=false）</li>
 * </ol>
 *
 * <h2>安全说明</h2>
 * <ul>
 *   <li>密码以 BCrypt 哈希存储于 {@code sys_user.password}，禁止明文</li>
 *   <li>暴力破解防护由 {@code AuthService.checkBruteForce} 统一处理
 *       （IP + username 双维度失败计数，复用既有 LoginProtectionProperties）</li>
 *   <li>登录成功/失败记录由 {@code AuthService} 统一审计</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 * @see LoginStrategy
 * @see UserService#getByUsername(String)
 * @see PasswordUtil
 */
@Slf4j
@Component
public class PasswordLoginStrategy implements LoginStrategy {

    /** 用户应用服务，用于按账号查询用户 */
    private final UserService userService;

    /** Token 服务，用于签发与存储 JWT Token 对 */
    private final TokenService tokenService;

    /** 登录响应装配器，统一构建 LoginResponse */
    private final LoginResponseHelper loginResponseHelper;

    /**
     * 构造账号密码登录策略。
     *
     * @param userService         用户应用服务
     * @param tokenService        Token 签发与存储服务
     * @param loginResponseHelper 登录响应装配器
     */
    public PasswordLoginStrategy(
            UserService userService, TokenService tokenService, LoginResponseHelper loginResponseHelper) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.loginResponseHelper = loginResponseHelper;
    }

    /**
     * 获取登录类型。
     *
     * @return {@link LoginType#PASSWORD}
     */
    @Override
    public LoginType getLoginType() {
        return LoginType.PASSWORD;
    }

    /**
     * 执行账号密码登录。
     *
     * @param request 登录请求，{@code username / password} 必填
     * @return 登录响应（含 Token 对与用户信息，isNewUser=false）
     * @throws BusinessException 参数缺失 / 账号不存在 / 账号禁用 / 密码错误时抛出
     */
    @Override
    public Mono<LoginResponse> login(LoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "用户名和密码不能为空"));
        }
        String deviceId = request.getDeviceId();
        return userService.getByUsername(username.trim())
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.USER_NOT_FOUND)))
                .flatMap(user -> authenticatePassword(user, password))
                .flatMap(user -> issueTokens(user, deviceId));
    }

    /**
     * 校验账号状态与密码哈希。
     *
     * @param user     查询到的用户实体
     * @param password 明文密码
     * @return 校验通过的用户实体
     * @throws BusinessException 账号禁用或密码错误时抛出
     */
    private Mono<UserEntity> authenticatePassword(UserEntity user, String password) {
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("账号密码登录失败：账号被禁用，username: {}", user.getUsername());
            return Mono.error(new BusinessException(ResultCode.LOGIN_FAILED));
        }
        if (user.getPassword() == null || !PasswordUtil.verifyPassword(password, user.getPassword())) {
            log.warn("账号密码登录失败：密码校验不通过，username: {}", user.getUsername());
            return Mono.error(new BusinessException(ResultCode.LOGIN_FAILED));
        }
        return Mono.just(user);
    }

    /**
     * 签发并存储 JWT Token 对，装配登录响应。
     *
     * @param user     认证通过的用户实体
     * @param deviceId 客户端设备标识
     * @return 完整登录响应
     */
    private Mono<LoginResponse> issueTokens(UserEntity user, String deviceId) {
        String subject = user.getOpenId() != null ? user.getOpenId() : String.valueOf(user.getId());
        String accessToken = tokenService.generateAccessToken(subject, deviceId);
        String refreshToken = tokenService.generateRefreshToken(subject, deviceId);
        return tokenService
                .storeToken(subject, accessToken, refreshToken)
                .thenReturn(loginResponseHelper.build(user, accessToken, refreshToken, false));
    }
}
