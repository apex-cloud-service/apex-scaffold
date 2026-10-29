package com.apex.scaffold.application.service.login;

import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.application.service.auth.AuthTicketService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.enums.LoginType;
import com.apex.scaffold.domain.gateway.ChuanglanGateway;
import com.apex.scaffold.domain.service.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 创蓝一键登录策略
 *
 * <p>继承 {@link AbstractPhoneLoginStrategy} 抽象基类，提供基于创蓝闪验SDK的一键登录功能。
 * 采用模板方法模式，只需实现 {@link #authenticate(LoginRequest)} 方法完成手机号认证，
 * 其余的用户查询/创建、JWT Token 生成等公共流程由基类统一处理。
 *
 * <h2>设计说明</h2>
 * <ol>
 *   <li>App端集成创蓝闪验SDK，初始化后预取号（获取临时token）</li>
 *   <li>用户点击"一键登录"按钮，SDK拉起运营商授权页</li>
 *   <li>用户授权后，SDK通过回调返回临时token</li>
 *   <li>App将token通过登录接口传入服务端</li>
 *   <li>本策略 {@link #authenticate(LoginRequest)} 调用创蓝API置换手机号</li>
 *   <li>基类 {@link AbstractPhoneLoginStrategy#login(LoginRequest)} 完成后续流程</li>
 * </ol>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>当闪验取号失败（如关闭数据网络、双卡切换等），App可引导用户切换至
 *   {@link ChuanglanSmsLoginStrategy 短信验证码登录}方式</li>
 *   <li>Token失效或过期时，App需重新调用SDK获取新token</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see AbstractPhoneLoginStrategy
 * @see ChuanglanGateway
 * @see ChuanglanSmsLoginStrategy
 */
@Slf4j
@Component
public class ChuanglanPhoneLoginStrategy extends AbstractPhoneLoginStrategy {

    /** 创蓝云平台网关，用于调用闪验API置换手机号 */
    private final ChuanglanGateway chuanglanGateway;

    /**
     * 构造创蓝一键登录策略
     *
     * @param chuanglanGateway    创蓝云平台网关
     * @param userService         用户应用服务
     * @param tokenService        Token 服务
     * @param loginResponseHelper 登录响应构建器
     * @param authTicketService 认证票据服务，登录成功后签发会话票据
     */
    public ChuanglanPhoneLoginStrategy(
            ChuanglanGateway chuanglanGateway,
            UserService userService,
            TokenService tokenService,
            LoginResponseHelper loginResponseHelper,
            AuthTicketService authTicketService) {
        super(userService, tokenService, loginResponseHelper, authTicketService);
        this.chuanglanGateway = chuanglanGateway;
    }

    /**
     * 执行创蓝闪验认证并获取手机号。
     *
     * <p>使用创蓝闪验token（clToken）调用 {@link ChuanglanGateway#getPhoneNumberByToken(String, String, String, String, String)}
     * 置换用户手机号。P0-CL-9修复：根据请求中的 platform 字段选择对应平台的 AppID/AppKey。</p>
     *
     * @param request 登录请求参数，必须包含创蓝闪验token（clToken字段）和可选的platform
     * @return 认证通过后的手机号 Mono
     * @throws BusinessException 如果闪验token为空或获取手机号失败
     */
    @Override
    protected Mono<String> authenticate(LoginRequest request) {
        String clToken = request.getClToken();
        if (clToken == null || clToken.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "创蓝一键登录token不能为空"));
        }

        String platform = request.getPlatform();
        return chuanglanGateway
                .getPhoneNumberByToken(clToken, null, null, null, platform)
                .flatMap(phoneResult -> {
                    if (!phoneResult.isSuccess()) {
                        log.warn(
                                "创蓝一键登录获取手机号失败, errorCode: {}, errorMessage: {}",
                                phoneResult.getErrorCode(),
                                phoneResult.getErrorMessage());
                        return Mono.<String>error(new BusinessException(
                                ResultCode.LOGIN_FAILED,
                                phoneResult.getErrorMessage() != null ? phoneResult.getErrorMessage() : "获取手机号失败"));
                    }

                    String phone = phoneResult.getPhone();
                    if (phone == null || phone.isBlank()) {
                        return Mono.error(new BusinessException(ResultCode.LOGIN_FAILED, "获取手机号失败"));
                    }
                    return Mono.just(phone);
                });
    }

    /**
     * 返回当前策略对应的登录类型
     *
     * @return {@link LoginType#CL_PHONE} 创蓝一键登录
     */
    @Override
    public LoginType getLoginType() {
        return LoginType.CL_PHONE;
    }
}
