package com.apex.scaffold.application.service.login;

import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.application.service.auth.AuthTicketService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.enums.LoginType;
import com.apex.scaffold.domain.gateway.WeChatGateway;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.service.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 微信一键登录策略
 *
 * <p>继承 {@link AbstractPhoneLoginStrategy} 抽象基类，提供基于微信手机号授权的快速登录功能。
 * 采用模板方法模式，只需实现 {@link #authenticate(LoginRequest)} 方法完成手机号认证，
 * 其余的用户查询/创建、JWT Token 生成等公共流程由基类统一处理。
 *
 * <h2>设计说明</h2>
 * <ol>
 *   <li>使用手机号授权凭证(phoneCode)调用微信接口获取用户手机号</li>
 *   <li>本策略 {@link #authenticate(LoginRequest)} 调用微信API获取手机号</li>
 *   <li>基类 {@link AbstractPhoneLoginStrategy#login(LoginRequest)} 完成后续流程</li>
 * </ol>
 *
 * <h2>设计说明</h2>
 * <p>用户登录后，若该用户已有存储的微信 access_token（通过微信授权码登录获取），
 * 则在 {@link #postAuthentication(UserEntity, LoginRequest)} 钩子中异步拉取微信用户信息
 * 并同步昵称、头像、性别、地区等字段。同步策略与 {@link WeChatCodeLoginStrategy} 一致：
 * 注册时选定的性别不会被微信覆盖。</p>
 *
 * @author Apex Team
 * @version 2.1.0
 * @since 1.0.0
 * @see AbstractPhoneLoginStrategy
 * @see WeChatGateway
 * @see WeChatCodeLoginStrategy
 */
@Slf4j
@Component
public class WeChatPhoneLoginStrategy extends AbstractPhoneLoginStrategy {

    /** 微信网关，用于调用微信开放平台接口获取手机号 */
    private final WeChatGateway weChatGateway;

    /**
     * 构造微信一键登录策略
     *
     * @param weChatGateway       微信网关
     * @param userService         用户应用服务
     * @param tokenService        Token 服务
     * @param loginResponseHelper 登录响应构建器
     * @param authTicketService 认证票据服务，登录成功后签发会话票据
     */
    public WeChatPhoneLoginStrategy(
            WeChatGateway weChatGateway,
            UserService userService,
            TokenService tokenService,
            LoginResponseHelper loginResponseHelper,
            AuthTicketService authTicketService) {
        super(userService, tokenService, loginResponseHelper, authTicketService);
        this.weChatGateway = weChatGateway;
    }

    /**
     * 执行微信手机号认证并获取手机号
     *
     * <p>使用手机号授权凭证(phoneCode)调用 {@link WeChatGateway#getPhoneNumber(String)}
     * 获取用户手机号。获取成功后返回手机号交由基类完成登录流程。
     *
     * @param request 登录请求参数，必须包含有效的手机号授权凭证
     * @return 认证通过后的手机号 Mono
     * @throws BusinessException 如果手机号授权凭证为空或获取手机号失败
     */
    @Override
    protected Mono<String> authenticate(LoginRequest request) {
        String phoneCode = request.getPhoneCode();
        if (phoneCode == null || phoneCode.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "手机号授权凭证不能为空"));
        }

        return weChatGateway.getPhoneNumber(phoneCode).flatMap(phoneResult -> {
            String phone = phoneResult.getPhoneNumber();
            if (phone == null || phone.isBlank()) {
                return Mono.<String>error(new BusinessException(ResultCode.WX_LOGIN_FAILED, "获取手机号失败"));
            }
            return Mono.just(phone);
        });
    }

    /**
     * 微信手机号登录后同步微信用户资料（异步钩子）。
     *
     * <p>若用户实体中已有存储的微信 access_token（通常由微信授权码登录时写入），
     * 则利用该 token 调用微信 {@code /sns/userinfo} 接口拉取最新的微信用户资料
     * （昵称、头像、性别、地区等）并同步到数据库。</p>
     *
     * <p><strong>同步策略（与 WeChatCodeLoginStrategy 一致）</strong></p>
     * <ul>
     *   <li><b>性别</b>：注册时选定的性别不可被微信覆盖，仅数据库性别为空时写入</li>
     *   <li><b>昵称</b>：微信昵称不为空时更新</li>
     *   <li><b>头像</b>：微信头像URL不为空时更新</li>
     *   <li><b>地区</b>：微信返回的国家/省份/城市信息不为空时更新</li>
     * </ul>
     *
     * <p><strong>容错策略</strong></p>
     * <p>微信接口调用失败（网络超时、token过期等）仅记录警告日志，不阻断登录流程。
     * 用户现有的性别等字段保持不变。</p>
     *
     * @param user    已有用户实体（通过手机号查询得到）
     * @param request 原始登录请求参数
     */
    @Override
    protected Mono<Void> postAuthentication(UserEntity user, LoginRequest request) {
        String accessToken = user.getAccessToken();
        String openId = user.getOpenId();
        // 无存储的微信access_token或openId时跳过同步
        if (accessToken == null || accessToken.isBlank() || openId == null || openId.isBlank()) {
            return Mono.empty();
        }
        return weChatGateway
                .getUserInfo(accessToken, openId)
                .doOnNext(wxInfo -> {
                    applyWeChatUserInfo(user, wxInfo);
                    // 异步更新用户实体到数据库
                    userService
                            .update(user)
                            .subscribe(
                                    updated -> log.debug(
                                            "WeChatPhoneLogin: 微信资料同步成功, userId={}, openId={}",
                                            updated.getId(),
                                            openId),
                                    error -> log.warn(
                                            "WeChatPhoneLogin: 微信资料同步后更新数据库失败, userId={}, error={}",
                                            user.getId(),
                                            error.getMessage()));
                })
                .onErrorResume(e -> {
                    log.warn("WeChatPhoneLogin: 拉取微信用户信息失败, openId={}, error={}", openId, e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    /**
     * 将微信用户信息同步到用户实体。
     *
     * <p>从微信接口返回的用户信息中提取昵称、头像、性别、地区等字段并设置到用户实体。
     * 遵循以下合并规则以确保数据安全：</p>
     * <ul>
     *   <li><b>性别</b>：优先保留数据库中已有的性别（注册时选定不可覆盖）；
     *        仅当数据库性别为空且微信返回有效性别（1-男/2-女）时才写入</li>
     *   <li><b>昵称</b>：微信昵称不为空时更新</li>
     *   <li><b>头像</b>：微信头像URL不为空时更新</li>
     *   <li><b>地区</b>：微信返回的国家/省份/城市信息不为空时更新</li>
     * </ul>
     *
     * @param user       目标用户实体
     * @param wxUserInfo 微信用户信息
     */
    private void applyWeChatUserInfo(UserEntity user, WeChatGateway.WeChatUserInfo wxUserInfo) {
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
                && (wxUserInfo.getGender() == 1 || wxUserInfo.getGender() == 2);
    }

    /**
     * 返回当前策略对应的登录类型
     *
     * @return {@link LoginType#WX_PHONE} 微信一键登录
     */
    @Override
    public LoginType getLoginType() {
        return LoginType.WX_PHONE;
    }
}
