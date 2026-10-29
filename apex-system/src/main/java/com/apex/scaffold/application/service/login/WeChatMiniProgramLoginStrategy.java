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
import com.apex.scaffold.domain.enums.SocialChannel;
import com.apex.scaffold.domain.gateway.WeChatGateway;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 微信小程序登录策略（WX_MINI）。
 *
 * <p>服务端以 {@code wx.login()} 的临时凭证调用 jscode2session 换取
 * OpenID / UnionID，按「统一账户 + 社交绑定表」模型完成多端账户归一；
 * 小程序 OpenID 不写入 {@code sys_user.open_id}（该列保留开放平台 App 渠道
 * 主身份），渠道身份统一登记在 {@code sys_user_social}（渠道=WECHAT_MINI）。
 *
 * <h2>设计说明</h2>
 * <ol>
 *   <li>按 {@code (WECHAT_MINI, openid)} 查社交绑定 → 命中则登录该账户</li>
 *   <li>未命中且有 UnionID → 按 {@code union_id} 查绑定 → 命中则登录并补当前渠道绑定行
 *       （同一微信开放平台主体下自动归一）</li>
 *   <li>未命中且携带手机号授权凭证（phoneCode）→ 置换手机号 → 按手机号命中账户
 *       则登录并补绑定</li>
 *   <li>均未命中：无 phoneCode 时签发 {@code WECHAT_BIND} 票据引导绑定手机号；
 *       phoneCode 置换出的手机号未注册时签发 {@code REGISTRATION} 票据引导注册</li>
 * </ol>
 *
 * <h2>设计说明</h2>
 * <p>依赖 {@code wechat.miniapp} 配置；凭据缺失时网关透传
 * {@code WECHAT_CHANNEL_NOT_CONFIGURED} 明确错误码，不影响其他登录方式。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 * @see LoginStrategy
 * @see WeChatGateway#miniProgramCodeToSession(String)
 * @see com.apex.scaffold.domain.enums.SocialChannel#WECHAT_MINI
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeChatMiniProgramLoginStrategy implements LoginStrategy {

    /** 微信网关（小程序渠道：jscode2session / 手机号置换） */
    private final WeChatGateway weChatGateway;

    /** 用户应用服务，提供用户查询能力 */
    private final UserService userService;

    /** 社交身份绑定服务（多端账户归一） */
    private final UserSocialService userSocialService;

    /** Token 服务（多会话模型签发） */
    private final TokenService tokenService;

    /** 登录响应构建器 */
    private final LoginResponseHelper loginResponseHelper;

    /** 一次性票据服务（绑定 / 注册票据签发） */
    private final AuthTicketService authTicketService;

    /**
     * 返回当前策略对应的登录类型
     *
     * @return {@link LoginType#WX_MINI}
     */
    @Override
    public LoginType getLoginType() {
        return LoginType.WX_MINI;
    }

    /**
     * 执行小程序登录（账户归一四步）。
     *
     * @param request 登录请求，{@code code} 必填、{@code phoneCode} 可选
     * @return 登录响应；新用户按票据返回 {@code nextAction} 引导后续流程
     */
    @Override
    public Mono<LoginResponse> login(LoginRequest request) {
        String code = request.getCode();
        if (code == null || code.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "小程序登录凭证不能为空"));
        }
        String clientType = ClientType.fromCode(request.getClientType()).name();
        String deviceId = request.getDeviceId();
        return weChatGateway.miniProgramCodeToSession(code).flatMap(session -> {
            String openid = session.getOpenid();
            String unionid = session.getUnionid();
            if (openid == null || openid.isBlank()) {
                return Mono.error(new BusinessException(ResultCode.WX_LOGIN_FAILED, "小程序登录失败"));
            }
            // 归一步骤 1：渠道绑定命中 → 直接登录
            return userSocialService
                    .findByChannelAndOpenId(SocialChannel.WECHAT_MINI, openid)
                    .flatMap(binding -> userService
                            .getById(binding.getUserId())
                            .switchIfEmpty(Mono.error(new BusinessException(ResultCode.WX_LOGIN_FAILED, "绑定用户不存在")))
                            .flatMap(user -> loginAndBind(user, openid, unionid, clientType, request)))
                    // 归一步骤 2/3/4
                    .switchIfEmpty(
                            Mono.defer(() -> resolveByUnionIdOrPhone(openid, unionid, clientType, deviceId, request)));
        });
    }

    /**
     * 归一步骤 2：按 UnionID 归一（同一微信开放平台主体下各端 UnionID 一致）。
     *
     * <p>命中绑定后登录目标账户并补当前渠道绑定行；未命中进入手机号归一。</p>
     *
     * @param openid     小程序 OpenID
     * @param unionid    微信 UnionID，可能为空
     * @param clientType 客户端类型编码
     * @param deviceId   设备标识
     * @param request    登录请求
     * @return 登录响应
     */
    private Mono<LoginResponse> resolveByUnionIdOrPhone(
            String openid, String unionid, String clientType, String deviceId, LoginRequest request) {
        Mono<UserEntity> byUnionId = unionid == null || unionid.isBlank()
                ? Mono.empty()
                : userSocialService
                        .findByUnionId(unionid)
                        .flatMap(binding -> userService.getById(binding.getUserId()))
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("社交绑定指向的用户不存在, channel=WECHAT_MINI, unionId 归一失败");
                            return Mono.empty();
                        }));
        return byUnionId
                .flatMap(user -> loginAndBind(user, openid, unionid, clientType, request))
                // 归一步骤 3/4：手机号归一或票据引导
                .switchIfEmpty(Mono.defer(() -> resolveByPhoneCode(openid, unionid, clientType, deviceId, request)));
    }

    /**
     * 归一步骤 3/4：手机号归一或票据引导。
     *
     * <p>携带 phoneCode 时置换手机号：命中已注册账户则登录并补绑定（步骤 3）；
     * 未注册则签发 REGISTRATION 票据引导注册（步骤 4b）。未携带 phoneCode 时
     * 签发 WECHAT_BIND 票据引导绑定手机号（步骤 4a）。</p>
     *
     * @param openid     小程序 OpenID
     * @param unionid    微信 UnionID，可能为空
     * @param clientType 客户端类型编码
     * @param deviceId   设备标识
     * @param request    登录请求
     * @return 登录响应
     */
    private Mono<LoginResponse> resolveByPhoneCode(
            String openid, String unionid, String clientType, String deviceId, LoginRequest request) {
        String phoneCode = request.getPhoneCode();
        if (phoneCode == null || phoneCode.isBlank()) {
            // 归一步骤 4a：无手机号凭证 → 签发绑定票据，引导短信验证绑定手机号
            return userService
                    .assertWechatRegistrationAllowed(openid)
                    .then(issueWechatBindTicket(openid, unionid, deviceId, request));
        }
        return weChatGateway.miniProgramPhoneNumber(phoneCode).flatMap(phoneResult -> {
            String phone = phoneResult.getPurePhoneNumber() != null
                            && !phoneResult.getPurePhoneNumber().isBlank()
                    ? phoneResult.getPurePhoneNumber()
                    : phoneResult.getPhoneNumber();
            if (phone == null || phone.isBlank()) {
                return Mono.error(new BusinessException(ResultCode.WX_LOGIN_FAILED, "获取小程序手机号失败"));
            }
            // 归一步骤 3：手机号命中已注册账户 → 登录并补绑定
            return userService
                    .getByPhone(phone)
                    .flatMap(user -> loginAndBind(user, openid, unionid, clientType, request))
                    // 归一步骤 4b：手机号未注册 → 签发注册票据
                    .switchIfEmpty(Mono.defer(() -> userService
                            .assertPhoneRegistrationAllowed(phone)
                            .then(userService.assertWechatRegistrationAllowed(openid))
                            .then(issueRegistrationTicket(openid, unionid, phone, deviceId))));
        });
    }

    /**
     * 登录命中账户并幂等登记小程序社交绑定。
     *
     * <p>绑定失败仅告警不阻断登录；会话令牌按多会话模型签发（新 sid）。</p>
     *
     * @param user       命中的用户实体
     * @param openid     小程序 OpenID
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
                .upsertBinding(user.getId(), user.getTenantId(), SocialChannel.WECHAT_MINI, openid, unionid)
                .onErrorResume(error -> {
                    log.warn("小程序社交身份绑定登记失败(不阻断登录): userId={}, error={}", user.getId(), error.getMessage());
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
     * <p>票据载荷携带小程序 OpenID / UnionID 与渠道（WECHAT_MINI），短信验证
     * 绑定手机号后按渠道写社交绑定行，不覆写 {@code sys_user.open_id}。</p>
     *
     * @param openid   小程序 OpenID
     * @param unionid  微信 UnionID，可能为空
     * @param deviceId 设备标识
     * @param request  登录请求（推导注册来源）
     * @return 待绑定登录响应（含 {@code nextAction=PHONE_BIND_REQUIRED}）
     */
    private Mono<LoginResponse> issueWechatBindTicket(
            String openid, String unionid, String deviceId, LoginRequest request) {
        AuthTicketPayload payload = new AuthTicketPayload();
        payload.setType(AuthTicketType.WECHAT_BIND);
        payload.setDeviceId(deviceId);
        payload.setOpenId(openid);
        payload.setUnionId(unionid);
        payload.setChannel(SocialChannel.WECHAT_MINI.name());
        payload.setRegisterChannel(com.apex.scaffold.domain.enums.RegisterChannel.MINI_PROGRAM.name());
        return authTicketService.issue(payload).map(ticket -> {
            LoginResponse response = new LoginResponse();
            response.setNextAction(AuthNextAction.PHONE_BIND_REQUIRED.name());
            response.setAuthTicket(ticket);
            response.setAuthTicketExpiresIn(AuthTicketService.EXPIRES_IN_SECONDS);
            return response;
        });
    }

    /**
     * 签发注册票据（{@code REGISTRATION}），返回待注册登录响应。
     *
     * <p>phoneCode 置换出的手机号可信（微信授权凭证服务端换取），直接随票据
     * 携带；渠道固定 WECHAT_MINI，注册完成后写小程序社交绑定行。</p>
     *
     * @param openid   小程序 OpenID
     * @param unionid  微信 UnionID，可能为空
     * @param phone    已由 phoneCode 置换出的手机号
     * @param deviceId 设备标识
     * @return 待注册登录响应（含 {@code nextAction=REGISTER_REQUIRED}）
     */
    private Mono<LoginResponse> issueRegistrationTicket(String openid, String unionid, String phone, String deviceId) {
        AuthTicketPayload payload = new AuthTicketPayload();
        payload.setType(AuthTicketType.REGISTRATION);
        payload.setDeviceId(deviceId);
        payload.setPhone(phone);
        payload.setOpenId(openid);
        payload.setUnionId(unionid);
        payload.setChannel(SocialChannel.WECHAT_MINI.name());
        payload.setRegisterChannel(com.apex.scaffold.domain.enums.RegisterChannel.MINI_PROGRAM.name());
        return authTicketService.issue(payload).map(ticket -> {
            LoginResponse response = new LoginResponse();
            response.setNextAction(AuthNextAction.REGISTER_REQUIRED.name());
            response.setAuthTicket(ticket);
            response.setAuthTicketExpiresIn(AuthTicketService.EXPIRES_IN_SECONDS);
            return response;
        });
    }
}
