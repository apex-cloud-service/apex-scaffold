package com.apex.scaffold.application.service.login;

import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.application.service.auth.AuthTicketService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.enums.LoginType;
import com.apex.scaffold.domain.service.SmsCodeService;
import com.apex.scaffold.domain.service.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 短信验证码登录策略
 *
 * <p>继承 {@link AbstractPhoneLoginStrategy} 抽象基类，提供基于手机短信验证码的登录功能。
 * 采用模板方法模式，只需实现 {@link #authenticate(LoginRequest)} 方法完成短信验证码校验，
 * 其余的用户查询/创建、JWT Token 生成等公共流程由基类统一处理。
 *
 * <h2>设计说明</h2>
 * <ol>
 *   <li>App 端调用 {@code POST /api/auth/sms/code} 传入手机号，系统发送验证码短信</li>
 *   <li>用户输入收到的 6 位验证码</li>
 *   <li>App 端调用 {@code POST /api/auth/login} 传入手机号和验证码</li>
 *   <li>本策略 {@link #authenticate(LoginRequest)} 验证验证码有效性</li>
 *   <li>基类 {@link AbstractPhoneLoginStrategy#login(LoginRequest)} 完成后续流程</li>
 * </ol>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>验证码有效期 5 分钟，过期需重新获取</li>
 *   <li>验证码验证成功后会自动从存储中移除（一次性使用）</li>
 *   <li>新创建的用户没有 openId，后续可通过绑定微信授权码补充</li>
 *   <li>JWT 的 subject 优先使用 openId，不存在时使用手机号</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see AbstractPhoneLoginStrategy
 * @see SmsCodeService
 */
@Slf4j
@Component
public class SmsLoginStrategy extends AbstractPhoneLoginStrategy {

    /**
     * 短信验证码服务。
     *
     * <p>提供验证码发送和验证功能，用于短信验证码登录流程。</p>
     */
    private final SmsCodeService smsCodeService;

    /**
     * 构造短信验证码登录策略
     *
     * @param chuanglanSmsCodeService 创蓝短信验证码服务
     * @param userService             用户应用服务
     * @param tokenService            Token 服务
     * @param loginResponseHelper     登录响应构建器
     * @param authTicketService 认证票据服务，登录成功后签发会话票据
     */
    public SmsLoginStrategy(
            @Qualifier("chuanglanSmsCodeService") SmsCodeService chuanglanSmsCodeService,
            UserService userService,
            TokenService tokenService,
            LoginResponseHelper loginResponseHelper,
            AuthTicketService authTicketService) {
        super(userService, tokenService, loginResponseHelper, authTicketService);
        this.smsCodeService = chuanglanSmsCodeService;
    }

    /**
     * 执行短信验证码认证并获取手机号
     *
     * <p>校验请求参数中的手机号和验证码，调用 {@link SmsCodeService#validate(String, String)}
     * 验证验证码的有效性。验证通过后返回手机号交由基类完成登录流程。
     *
     * @param request 登录请求参数，必须包含手机号和验证码
     * @return 认证通过后的手机号 Mono
     * @throws BusinessException 如果手机号或验证码为空，或验证码错误/已过期
     */
    @Override
    protected Mono<String> authenticate(LoginRequest request) {
        String phone = request.getPhone();
        String smsCode = request.getSmsCode();

        if (phone == null || phone.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "手机号不能为空"));
        }
        if (smsCode == null || smsCode.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "短信验证码不能为空"));
        }

        return smsCodeService.validate(phone, smsCode).flatMap(valid -> {
            if (!valid) {
                return Mono.<String>error(new BusinessException(ResultCode.PARAM_ERROR, "验证码错误或已过期"));
            }
            return Mono.just(phone);
        });
    }

    /**
     * 返回当前策略对应的登录类型
     *
     * @return {@link LoginType#WX_SMS} 短信验证码登录
     */
    @Override
    public LoginType getLoginType() {
        return LoginType.WX_SMS;
    }
}
