package com.apex.scaffold.application.service.login;

import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.application.service.UserSocialService;
import com.apex.scaffold.application.service.auth.AuthNextAction;
import com.apex.scaffold.application.service.auth.AuthTicketPayload;
import com.apex.scaffold.application.service.auth.AuthTicketService;
import com.apex.scaffold.application.service.auth.AuthTicketType;
import com.apex.scaffold.common.dto.TokenStoreEntry;
import com.apex.scaffold.common.enums.ClientType;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.enums.LoginType;
import com.apex.scaffold.domain.enums.RegisterChannel;
import com.apex.scaffold.domain.enums.SocialChannel;
import com.apex.scaffold.domain.gateway.WeChatGateway;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 微信公众号网页授权登录策略（WX_OAUTH）。
 *
 * <p>公众号 H5 内通过网页授权（snsapi_base / snsapi_userinfo）获取授权 code，
 * 服务端以 {@code wechat.mp} 渠道凭据换取 OpenID / UnionID，按「统一账户 +
 * 社交绑定表」模型完成多端账户归一；公众号 OpenID 不写入 {@code sys_user.open_id}
 * （该列保留开放平台 App 渠道主身份），渠道身份统一登记在
 * {@code sys_user_social}（渠道=WECHAT_MP）。
 *
 * <h2>设计说明</h2>
 * <ol>
 *   <li>按 {@code (WECHAT_MP, openid)} 查社交绑定 → 命中则登录该账户</li>
 *   <li>未命中且有 UnionID → 按 {@code union_id} 查绑定 → 命中则登录并补当前渠道绑定行
 *       （同一微信开放平台主体下自动归一）</li>
 *   <li>（网页授权无手机号授权凭证，手机号归一由绑定/短信链路承担）</li>
 *   <li>均未命中：签发 {@code WECHAT_BIND} 票据，引导短信验证绑定手机号后完成注册/登录</li>
 * </ol>
 *
 * <h2>设计说明</h2>
 * <p>登录请求必须携带 {@code state}（由
 * {@code GET /api/v1/auth/wechat/mp/authorize} 签发，Redis 登记 5 分钟），
 * 服务端原子校验并消费（一次性），不匹配或已消费拒绝登录。</p>
 *
 * <h2>设计说明</h2>
 * <p>依赖 {@code wechat.mp} 配置；凭据缺失时网关透传
 * {@code WECHAT_CHANNEL_NOT_CONFIGURED} 明确错误码，不影响其他登录方式。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 * @see LoginStrategy
 * @see WeChatGateway#mpAuthCodeToSession(String)
 * @see com.apex.scaffold.domain.enums.SocialChannel#WECHAT_MP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeChatMpAuthLoginStrategy implements LoginStrategy {

    /** 微信网关（公众号渠道：网页授权 / state 防 CSRF） */
    private final WeChatGateway weChatGateway;

    /** 用户应用服务，提供用户查询与注册前置校验能力 */
    private final UserService userService;

    /** 社交身份绑定服务（多端账户归一） */
    private final UserSocialService userSocialService;

    /** Token 服务（多会话模型签发） */
    private final TokenService tokenService;

    /** 登录响应构建器 */
    private final LoginResponseHelper loginResponseHelper;

    /** 一次性票据服务（绑定票据签发） */
    private final AuthTicketService authTicketService;

    /**
     * 返回当前策略对应的登录类型
     *
     * @return {@link LoginType#WX_OAUTH}
     */
    @Override
    public LoginType getLoginType() {
        return LoginType.WX_OAUTH;
    }

    /**
     * 执行公众号网页授权登录（state 校验 + 账户归一）。
     *
     * @param request 登录请求，{@code code} 与 {@code state} 必填
     * @return 登录响应；新用户返回 {@code nextAction=PHONE_BIND_REQUIRED} 引导绑定
     */
    @Override
    public Mono<LoginResponse> login(LoginRequest request) {
        String code = request.getCode();
        if (code == null || code.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "网页授权凭证不能为空"));
        }
        String state = request.getState();
        if (state == null || state.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "缺少 OAuth state，请从授权入口重新发起"));
        }
        String clientType = ClientType.fromCode(request.getClientType()).name();
        String deviceId = request.getDeviceId();
        return weChatGateway
                .mpValidateAuthState(state)
                .flatMap(valid -> Boolean.TRUE.equals(valid)
                        ? loginByCode(code, clientType, deviceId, request)
                        : Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "OAuth state 无效或已消费")));
    }

    /**
     * 授权 code 换取会话并执行账户归一。
     *
     * @param code       网页授权 code
     * @param clientType 客户端类型编码
     * @param deviceId   设备标识
     * @param request    登录请求
     * @return 登录响应
     */
    private Mono<LoginResponse> loginByCode(String code, String clientType, String deviceId, LoginRequest request) {
        return weChatGateway.mpAuthCodeToSession(code).flatMap(session -> {
            String openid = session.getOpenid();
            String unionid = session.getUnionid();
            if (openid == null || openid.isBlank()) {
                return Mono.error(new BusinessException(ResultCode.WX_LOGIN_FAILED, "公众号网页授权失败"));
            }
            // 归一步骤 1：渠道绑定命中 → 直接登录
            return userSocialService
                    .findByChannelAndOpenId(SocialChannel.WECHAT_MP, openid)
                    .flatMap(binding -> userService
                            .getById(binding.getUserId())
                            .switchIfEmpty(Mono.error(new BusinessException(ResultCode.WX_LOGIN_FAILED, "绑定用户不存在")))
                            .flatMap(user -> loginAndBind(user, openid, unionid, clientType, request)))
                    // 归一步骤 2/4
                    .switchIfEmpty(Mono.defer(() -> resolveByUnionId(openid, unionid, clientType, request)));
        });
    }

    /**
     * 归一步骤 2：按 UnionID 归一（同一微信开放平台主体下各端 UnionID 一致）。
     *
     * <p>命中绑定后登录目标账户并补当前渠道绑定行；未命中进入票据引导。</p>
     *
     * @param openid     公众号 OpenID
     * @param unionid    微信 UnionID，可能为空
     * @param clientType 客户端类型编码
     * @param request    登录请求
     * @return 登录响应
     */
    private Mono<LoginResponse> resolveByUnionId(
            String openid, String unionid, String clientType, LoginRequest request) {
        Mono<UserEntity> byUnionId = unionid == null || unionid.isBlank()
                ? Mono.empty()
                : userSocialService
                        .findByUnionId(unionid)
                        .flatMap(binding -> userService.getById(binding.getUserId()))
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("社交绑定指向的用户不存在, unionId 归一失败");
                            return Mono.empty();
                        }));
        return byUnionId
                .flatMap(user -> loginAndBind(user, openid, unionid, clientType, request))
                // 归一步骤 4：未命中 → 签发绑定票据引导短信验证
                .switchIfEmpty(Mono.defer(() -> userService
                        .assertWechatRegistrationAllowed(openid)
                        .then(issueWechatBindTicket(openid, unionid, request))));
    }

    /**
     * 登录命中账户并幂等登记公众号社交绑定。
     *
     * <p>绑定失败仅告警不阻断登录；会话令牌按多会话模型签发（新 sid）。</p>
     *
     * @param user       命中的用户实体
     * @param openid     公众号 OpenID
     * @param unionid    微信 UnionID，可能为空
     * @param clientType 客户端类型编码
     * @param request    登录请求
     * @return 登录响应
     */
    private Mono<LoginResponse> loginAndBind(
            UserEntity user, String openid, String unionid, String clientType, LoginRequest request) {
        String deviceId = request.getDeviceId();
        String subject = user.getOpenId() != null ? user.getOpenId() : String.valueOf(user.getId());
        Mono<Void> bindRegistration = userSocialService
                .upsertBinding(user.getId(), user.getTenantId(), SocialChannel.WECHAT_MP, openid, unionid)
                .onErrorResume(error -> {
                    log.warn("公众号社交身份绑定登记失败(不阻断登录): userId={}, error={}", user.getId(), error.getMessage());
                    return Mono.empty();
                });
        return bindRegistration
                .then(userService.updateLoginInfo(user.getId(), request.getClientIp()))
                .then(Mono.defer(() -> {
                    String sid = java.util.UUID.randomUUID().toString();
                    String accessToken = tokenService.generateAccessToken(subject, deviceId, sid, clientType);
                    String refreshToken = tokenService.generateRefreshToken(subject, deviceId, sid, clientType);
                    TokenStoreEntry tokenEntry = TokenStoreEntry.builder()
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
                            .deviceId(deviceId)
                            .build();
                    return tokenService
                            .storeSessionToken(subject, sid, accessToken, refreshToken, tokenEntry, tokenEntry)
                            .thenReturn(loginResponseHelper.build(
                                    user, accessToken, refreshToken, false, user.getPhone(), request.getClientIp()));
                }));
    }

    /**
     * 签发微信绑定票据（{@code WECHAT_BIND}），返回待绑定登录响应。
     *
     * <p>票据载荷携带公众号 OpenID / UnionID 与渠道（WECHAT_MP），短信验证
     * 绑定手机号后按渠道写社交绑定行，不覆写 {@code sys_user.open_id}。</p>
     *
     * @param openid  公众号 OpenID
     * @param unionid 微信 UnionID，可能为空
     * @param request 登录请求
     * @return 待绑定登录响应（含 {@code nextAction=PHONE_BIND_REQUIRED}）
     */
    private Mono<LoginResponse> issueWechatBindTicket(String openid, String unionid, LoginRequest request) {
        AuthTicketPayload payload = new AuthTicketPayload();
        payload.setType(AuthTicketType.WECHAT_BIND);
        payload.setDeviceId(request.getDeviceId());
        payload.setOpenId(openid);
        payload.setUnionId(unionid);
        payload.setChannel(SocialChannel.WECHAT_MP.name());
        payload.setRegisterChannel(RegisterChannel.WECHAT_MP.name());
        return authTicketService.issue(payload).map(ticket -> {
            LoginResponse response = new LoginResponse();
            response.setNextAction(AuthNextAction.PHONE_BIND_REQUIRED.name());
            response.setAuthTicket(ticket);
            response.setAuthTicketExpiresIn(AuthTicketService.EXPIRES_IN_SECONDS);
            return response;
        });
    }
}
