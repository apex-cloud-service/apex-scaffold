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
 * 创蓝短信验证码登录策略
 *
 * <p>继承 {@link AbstractPhoneLoginStrategy} 抽象基类，提供基于创蓝253短信平台的验证码登录功能。
 * 采用模板方法模式，只需实现 {@link #authenticate(LoginRequest)} 方法完成短信验证码校验，
 * 其余的用户查询/创建、JWT Token 生成等公共流程由基类统一处理。
 *
 * <h2>登录流程</h2>
 * <ol>
 *   <li>App端调用 {@code POST /api/auth/cl/sms/code} 传入手机号</li>
 *   <li>系统通过创蓝253短信平台发送6位验证码到用户手机</li>
 *   <li>用户输入收到的6位验证码</li>
 *   <li>App端调用 {@code POST /api/auth/login} 传入 loginType=CL_SMS、手机号和验证码</li>
 *   <li>本策略 {@link #authenticate(LoginRequest)} 验证验证码有效性</li>
 *   <li>基类 {@link AbstractPhoneLoginStrategy#login(LoginRequest)} 完成后续流程</li>
 * </ol>
 *
 * <h2>与现有登录方式的关系</h2>
 * <ul>
 *   <li>通过 {@link SmsCodeService} 统一接口 + {@code @Qualifier("chuanglanSmsCodeService")} 注入创蓝实现</li>
 *   <li>短信发送通过创蓝253短信平台，与原有服务商解耦</li>
 *   <li>验证码缓存独立命名空间（前缀 cl:sms:code:），防止与现有验证码冲突</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.1.0
 * @since 1.0.0
 * @see AbstractPhoneLoginStrategy
 * @see SmsCodeService
 * @see ChuanglanPhoneLoginStrategy
 */
@Slf4j
@Component
public class ChuanglanSmsLoginStrategy extends AbstractPhoneLoginStrategy {

    /** 统一短信验证码服务，通过 {@code @Qualifier("chuanglanSmsCodeService")} 注入创蓝253实现 */
    private final SmsCodeService chuanglanSmsCodeService;

    /**
     * 构造创蓝短信验证码登录策略
     *
     * @param chuanglanSmsCodeService 创蓝短信验证码服务（通过 {@code @Qualifier("chuanglanSmsCodeService")} 注入）
     * @param userService             用户应用服务
     * @param tokenService            Token 服务
     * @param loginResponseHelper     登录响应构建器
     * @param authTicketService 认证票据服务，登录成功后签发会话票据
     */
    public ChuanglanSmsLoginStrategy(
            @Qualifier("chuanglanSmsCodeService") SmsCodeService chuanglanSmsCodeService,
            UserService userService,
            TokenService tokenService,
            LoginResponseHelper loginResponseHelper,
            AuthTicketService authTicketService) {
        super(userService, tokenService, loginResponseHelper, authTicketService);
        this.chuanglanSmsCodeService = chuanglanSmsCodeService;
    }

    /**
     * 执行创蓝短信验证码认证并获取手机号
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

        return chuanglanSmsCodeService.validate(phone, smsCode).flatMap(valid -> {
            if (!valid) {
                return Mono.<String>error(new BusinessException(ResultCode.PARAM_ERROR, "验证码错误或已过期"));
            }
            return Mono.just(phone);
        });
    }

    /**
     * 返回当前策略对应的登录类型
     *
     * @return {@link LoginType#CL_SMS} 创蓝短信验证码登录
     */
    @Override
    public LoginType getLoginType() {
        return LoginType.CL_SMS;
    }
}
