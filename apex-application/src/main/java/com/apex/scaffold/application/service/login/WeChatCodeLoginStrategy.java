package com.apex.scaffold.application.service.login;

import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.application.service.UserSocialService;
import com.apex.scaffold.application.service.auth.AuthNextAction;
import com.apex.scaffold.application.service.auth.AuthTicketPayload;
import com.apex.scaffold.application.service.auth.AuthTicketService;
import com.apex.scaffold.application.service.auth.AuthTicketType;
import com.apex.scaffold.application.service.auth.RegisterChannelResolver;
import com.apex.scaffold.common.dto.TokenStoreEntry;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.enums.LoginType;
import com.apex.scaffold.domain.enums.SocialChannel;
import com.apex.scaffold.domain.gateway.WeChatGateway;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.service.TokenService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 微信验证码登录策略
 *
 * <p>
 * 实现 {@link LoginStrategy} 接口，提供基于微信 OAuth2 授权码的登录功能。 这是系统默认的登录方式，通过微信授权码换取 access_token 和 openId，
 * 再获取用户基本信息来完成用户注册或登录。
 *
 * <h2>登录流程</h2>
 * <ol>
 * <li>使用微信授权码(code)调用微信 OAuth2 接口获取 access_token 和 openId</li>
 * <li>根据 openId 查询系统用户（存在则更新，不存在则创建）</li>
 * <li>拉取微信用户基本信息（昵称、头像、地区等）并更新到用户实体</li>
 * <li>生成 JWT Token 对（accessToken + refreshToken）并存储</li>
 * <li>发布用户登录/注册领域事件</li>
 * </ol>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see LoginStrategy
 * @see WeChatGateway
 * @see TokenService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeChatCodeLoginStrategy implements LoginStrategy {

    private static final int WECHAT_OPENID_TYPE = 2;

    /** 微信网关，用于调用微信开放平台接口 */
    private final WeChatGateway weChatGateway;

    /** 用户应用服务，提供用户查询和创建功能 */
    private final UserService userService;

    /**
     * 社交身份绑定应用服务。
     *
     * <p>登录成功后登记（WECHAT_APP, openId）绑定记录，支撑多端统一账户归一。
     * 绑定失败不阻断登录，由调用方容错。</p>
     */
    private final UserSocialService userSocialService;

    /** Token 服务，提供 JWT 生成和存储功能 */
    private final TokenService tokenService;

    /** 登录响应构建器，负责构建统一格式的登录响应 */
    private final LoginResponseHelper loginResponseHelper;

    /**
     * 注册前微信身份票据服务。
     *
     * <p>用于在微信授权通过但用户尚未注册或未绑定手机号时签发临时凭证，
     * 注册/绑定完成后一次性消费。</p>
     */
    private final AuthTicketService authTicketService;

    /**
     * 执行微信验证码登录
     *
     * <p>
     * 使用微信授权码(code)完成 OAuth2 认证流程，包含以下步骤：
     * <ol>
     *   <li>使用微信授权码调用微信 OAuth2 接口获取 access_token 和 openId</li>
     *   <li>调用微信用户信息接口获取昵称、头像、性别等资料</li>
     *   <li>根据 openId 查询系统用户（存在则更新资料和 Token，不存在则进入手机号绑定流程）</li>
     *   <li>更新用户实体的昵称、头像、性别、地区等信息</li>
     *   <li>生成 JWT Token 对（accessToken + refreshToken）并存储</li>
     * </ol>
     *
     * <h3>性别字段处理</h3>
     * <p>登录时从微信用户信息中获取 gender 字段（0-未知/1-男/WECHAT_OPENID_TYPE-女），
     * 若用户已在系统中注册且数据库已存有性别值，则保持数据库原值不变（注册时选定的性别不可被微信覆盖）。
     * 若数据库中性别为空且微信返回了有效性别值，则更新为微信性别。
     *
     * @param request 登录请求参数，必须包含有效的微信授权码
     * @return 登录响应 Mono，包含 JWT Token 对和用户信息（含性别）
     * @throws BusinessException 如果微信授权失败或返回数据不完整
     */
    @Override
    public Mono<LoginResponse> login(LoginRequest request) {
        return weChatGateway.login(request.getCode()).flatMap(loginResult -> {
            String openId = loginResult.getOpenid();
            String unionId = loginResult.getUnionid();
            String wxAccessToken = loginResult.getAccessToken();
            String wxRefreshToken = loginResult.getRefreshToken();
            long expiresIn = loginResult.getExpiresIn() != null ? loginResult.getExpiresIn() : 7200L;

            if (openId == null || openId.isBlank() || wxAccessToken == null || wxAccessToken.isBlank()) {
                return Mono.<LoginResponse>error(new BusinessException(ResultCode.WX_LOGIN_FAILED, "微信登录失败"));
            }

            LocalDateTime tokenExpireTime = LocalDateTime.now().plusSeconds(expiresIn);
            // 拉取微信用户信息（昵称、头像、地区等），已存在用户用于资料同步，新用户通过票据链传递
            return fetchWeChatUserInfo(wxAccessToken, openId).flatMap(optionalWxInfo -> {
                WeChatGateway.WeChatUserInfo wxUserInfo = optionalWxInfo.orElse(null);
                return userService
                        .getByOpenId(openId)
                        .flatMap(existing -> {
                            if (existing.getPhone() == null
                                    || existing.getPhone().isBlank()) {
                                return issueWechatBindTicket(
                                        openId,
                                        unionId,
                                        wxAccessToken,
                                        wxRefreshToken,
                                        tokenExpireTime,
                                        request.getDeviceId(),
                                        wxUserInfo,
                                        request);
                            }
                            // 记录登录IP和时间
                            existing.recordLogin(request.getClientIp());
                            // 更新微信基础信息和Token，包括从微信同步性别
                            applyWeChatUserInfo(existing, wxUserInfo, unionId);
                            existing.updateToken(wxAccessToken, wxRefreshToken, tokenExpireTime);
                            return userService
                                    .update(existing)
                                    // 登记多端社交身份绑定（失败不阻断登录）
                                    .flatMap(user -> userSocialService
                                            .upsertBinding(
                                                    user.getId(),
                                                    user.getTenantId(),
                                                    SocialChannel.WECHAT_APP,
                                                    openId,
                                                    unionId)
                                            .onErrorResume(bindError -> {
                                                log.warn(
                                                        "微信社交身份绑定记录失败(不阻断登录): userId={}, error={}",
                                                        user.getId(),
                                                        bindError.getMessage());
                                                return Mono.empty();
                                            })
                                            .then(buildLoginResponse(user, request)));
                        })
                        .switchIfEmpty(Mono.defer(() -> userService
                                .assertWechatRegistrationAllowed(openId)
                                .then(issueWechatBindTicket(
                                        openId,
                                        unionId,
                                        wxAccessToken,
                                        wxRefreshToken,
                                        tokenExpireTime,
                                        request.getDeviceId(),
                                        wxUserInfo,
                                        request))));
            });
        });
    }

    /**
     * 拉取微信用户信息，失败或为空时不阻断流程。
     *
     * @param accessToken 微信 access_token
     * @param openId      用户 openId
     * @return 微信用户信息；网络异常或接口返回空时返回 {@code Optional.empty()} 的 Mono
     */
    private Mono<java.util.Optional<WeChatGateway.WeChatUserInfo>> fetchWeChatUserInfo(
            String accessToken, String openId) {
        return weChatGateway
                .getUserInfo(accessToken, openId)
                .map(java.util.Optional::of)
                .onErrorResume(e -> {
                    log.warn("获取微信用户信息失败, openId: {}, error: {}", openId, e.getMessage());
                    return Mono.just(java.util.Optional.empty());
                })
                .defaultIfEmpty(java.util.Optional.empty());
    }

    /**
     * 将微信用户信息同步到用户实体。
     *
     * <p>从微信接口返回的用户信息中提取昵称、头像、性别、地区等字段并设置到用户实体。
     * 遵循以下合并规则以确保数据安全：</p>
     * <ul>
     *   <li><b>性别</b>：优先保留数据库中已有的性别（注册时选定不可覆盖）；
     *        仅当数据库性别为空且微信返回有效性别（1-男/WECHAT_OPENID_TYPE-女）时才写入</li>
     *   <li><b>昵称</b>：微信昵称不为空时更新</li>
     *   <li><b>头像</b>：微信头像URL不为空时更新</li>
     *   <li><b>地区</b>：微信返回的国家/省份/城市信息不为空时更新</li>
     *   <li><b>UnionId</b>：微信返回 UnionId 不为空时更新</li>
     * </ul>
     *
     * @param user        目标用户实体
     * @param wxUserInfo  微信用户信息（可能为 null，表示获取失败）
     * @param unionId     微信 UnionId
     */
    private void applyWeChatUserInfo(UserEntity user, WeChatGateway.WeChatUserInfo wxUserInfo, String unionId) {
        if (unionId != null && !unionId.isBlank()) {
            user.setUnionId(unionId);
        }
        if (wxUserInfo == null) {
            return;
        }
        if (wxUserInfo.getNickname() != null && !wxUserInfo.getNickname().isBlank()) {
            user.setNickName(wxUserInfo.getNickname());
        }
        if (wxUserInfo.getAvatarUrl() != null && !wxUserInfo.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(wxUserInfo.getAvatarUrl());
        }
        // 性别合并策略：注册时选定的性别不可被微信覆盖，仅数据库为空时从微信同步
        if (shouldSyncGenderFromWechat(user, wxUserInfo)) {
            user.setGender(wxUserInfo.getGender());
        }
        if (wxUserInfo.getCountry() != null && !wxUserInfo.getCountry().isBlank()) {
            user.setCountry(wxUserInfo.getCountry());
        }
        if (wxUserInfo.getProvince() != null && !wxUserInfo.getProvince().isBlank()) {
            user.setProvince(wxUserInfo.getProvince());
        }
        if (wxUserInfo.getCity() != null && !wxUserInfo.getCity().isBlank()) {
            user.setCity(wxUserInfo.getCity());
        }
        if (wxUserInfo.getLanguage() != null && !wxUserInfo.getLanguage().isBlank()) {
            user.setLanguage(wxUserInfo.getLanguage());
        }
    }

    /**
     * 判断是否应该从微信同步性别信息。
     *
     * <p>业务规则：仅当用户性别为空且微信提供了有效性别值（1-男性或2-女性）时才同步。
     * 微信性别枚举：1-男性，2-女性，0-未设置。
     *
     * @param user 用户实体
     * @param wxUserInfo 微信用户信息
     * @return true 表示应该同步性别
     */
    private boolean shouldSyncGenderFromWechat(UserEntity user, WeChatGateway.WeChatUserInfo wxUserInfo) {
        return user.getGender() == null
                && wxUserInfo.getGender() != null
                && (wxUserInfo.getGender() == 1 || wxUserInfo.getGender() == WECHAT_OPENID_TYPE);
    }

    /**
     * 检查微信性别值是否有效。
     *
     * <p>有效性别值：1-男性，2-女性，0-未设置不同步。
     *
     * @param gender 微信性别值
     * @return true 表示有效
     */
    private boolean isValidWechatGender(Integer gender) {
        return gender != null && (gender == 1 || gender == WECHAT_OPENID_TYPE);
    }

    /**
     * 签发微信手机号绑定票据并返回待绑定登录响应。
     *
     * <p>将微信身份与用户资料写入注册票据，返回 {@link AuthNextAction#PHONE_BIND_REQUIRED}
     * 登录响应以引导用户完成手机号绑定。票据同时携带由登录请求推导的注册来源，
     * 供注册落库时写入 {@code sys_user.register_channel}。</p>
     *
     * @param openId          微信 openId
     * @param unionId         微信 unionId
     * @param accessToken     微信 Access Token
     * @param refreshToken    微信 Refresh Token
     * @param tokenExpireTime 微信 Token 过期时间
     * @param deviceId        设备 ID
     * @param wxUserInfo      微信用户资料
     * @param request         原始登录请求，用于推导注册来源
     * @return 待绑定登录响应（含注册票据）
     */
    private Mono<LoginResponse> issueWechatBindTicket(
            String openId,
            String unionId,
            String accessToken,
            String refreshToken,
            LocalDateTime tokenExpireTime,
            String deviceId,
            WeChatGateway.WeChatUserInfo wxUserInfo,
            LoginRequest request) {
        AuthTicketPayload payload = new AuthTicketPayload();
        payload.setType(AuthTicketType.WECHAT_BIND);
        payload.setDeviceId(deviceId);
        payload.setOpenId(openId);
        payload.setUnionId(unionId);
        payload.setWechatAccessToken(accessToken);
        payload.setWechatRefreshToken(refreshToken);
        payload.setWechatTokenExpireTime(tokenExpireTime);
        payload.setRegisterChannel(RegisterChannelResolver.resolve(request).name());
        // 将微信用户资料（昵称、头像、地区等）写入票据，确保注册阶段不会丢失
        if (wxUserInfo != null) {
            payload.setNickName(wxUserInfo.getNickname());
            payload.setAvatarUrl(wxUserInfo.getAvatarUrl());
            payload.setCountry(wxUserInfo.getCountry());
            payload.setProvince(wxUserInfo.getProvince());
            payload.setCity(wxUserInfo.getCity());
            payload.setLanguage(wxUserInfo.getLanguage());
            // 性别数据同步：仅同步有效性别值（1-男性，2-女性），0-未设置不同步
            if (isValidWechatGender(wxUserInfo.getGender())) {
                payload.setGender(wxUserInfo.getGender());
            }
        }
        return authTicketService.issue(payload).map(ticket -> {
            LoginResponse response = new LoginResponse();
            response.setNextAction(AuthNextAction.PHONE_BIND_REQUIRED.name());
            response.setAuthTicket(ticket);
            response.setAuthTicketExpiresIn(AuthTicketService.EXPIRES_IN_SECONDS);
            return response;
        });
    }

    /**
     * 为已登录用户生成并存储 Token，构建完整登录响应。
     *
     * <p>多会话模型：每次登录生成新会话ID（{@code sid}），令牌按会话槽独立存储，
     * 多端同时在线互不覆盖；客户端类型取请求头/请求体上报值（缺省 UNKNOWN）。</p>
     *
     * @param user    已登录的用户实体
     * @param request 微信验证码登录请求
     * @return 登录响应（含 Token）
     */
    private Mono<LoginResponse> buildLoginResponse(UserEntity user, LoginRequest request) {
        String deviceId = request.getDeviceId();
        String sid = java.util.UUID.randomUUID().toString();
        String clientType = com.apex.scaffold.common.enums.ClientType.fromCode(request.getClientType())
                .name();
        String accessToken = tokenService.generateAccessToken(user.getOpenId(), deviceId, sid, clientType);
        String refreshToken = tokenService.generateRefreshToken(user.getOpenId(), deviceId, sid, clientType);
        TokenStoreEntry tokenEntry = buildTokenEntry(user, sid, clientType, request);
        return tokenService
                .storeSessionToken(user.getOpenId(), sid, accessToken, refreshToken, tokenEntry, tokenEntry)
                .thenReturn(loginResponseHelper.build(
                        user, accessToken, refreshToken, false, user.getPhone(), request.getClientIp()));
    }

    /**
     * 从用户实体构建 Token 存储条目（多会话模型，携带会话与终端上下文）。
     *
     * @param user       用户实体
     * @param sid        会话ID
     * @param clientType 客户端类型编码
     * @param request    登录请求（提取 platform / deviceId）
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

    /**
     * 返回当前策略对应的登录类型
     *
     * @return {@link LoginType#WX_CODE} 微信验证码登录
     */
    @Override
    public LoginType getLoginType() {
        return LoginType.WX_CODE;
    }
}
