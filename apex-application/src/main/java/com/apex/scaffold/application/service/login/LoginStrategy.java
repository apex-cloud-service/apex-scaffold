package com.apex.scaffold.application.service.login;

import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.domain.enums.LoginType;
import reactor.core.publisher.Mono;

/**
 * 登录策略接口
 *
 * <p>定义登录操作的统一规范，是策略模式的核心接口。
 * 不同的登录方式通过实现该接口来提供差异化的登录逻辑，
 * 由 {@link LoginStrategyProvider} 根据请求中的登录类型动态选择具体实现。
 *
 * <h2>实现规范</h2>
 * <ul>
 *   <li>所有实现类必须标注 {@link org.springframework.stereotype.Component} 注解</li>
 *   <li>实现类需实现 {@link #getLoginType()} 返回对应的登录类型，由 {@link LoginStrategyProvider} 自动注册</li>
 *   <li>登录成功后需生成 JWT Token 对（accessToken + refreshToken）</li>
 *   <li>新注册用户需发布 {@link com.apex.scaffold.domain.event.UserRegisteredEvent} 注册事件；
 *       {@link com.apex.scaffold.domain.event.UserLoggedInEvent} 登录事件当前无消费者，暂不发布</li>
 * </ul>
 *
 * <h2>现有实现</h2>
 * <ul>
 *   <li>{@link WeChatCodeLoginStrategy} - 微信验证码登录</li>
 *   <li>{@link WeChatPhoneLoginStrategy} - 微信一键登录</li>
 *   <li>{@link SmsLoginStrategy} - 短信验证码登录</li>
 *   <li>{@link ChuanglanPhoneLoginStrategy} - 创蓝一键登录</li>
 *   <li>{@link ChuanglanSmsLoginStrategy} - 创蓝短信验证码登录</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 * @see LoginStrategyProvider
 * @see WeChatCodeLoginStrategy
 * @see WeChatPhoneLoginStrategy
 * @see SmsLoginStrategy
 * @see ChuanglanPhoneLoginStrategy
 * @see ChuanglanSmsLoginStrategy
 */
public interface LoginStrategy {

    /**
     * 执行登录操作
     *
     * <p>根据请求中的登录凭证执行完整的登录流程，包括：
     * <ol>
     *   <li>调用微信接口获取用户身份信息</li>
     *   <li>根据身份信息查询或创建系统用户</li>
     *   <li>生成并存储 JWT Token 对</li>
     *   <li>发布领域事件</li>
     *   <li>返回登录响应</li>
     * </ol>
     *
     * @param request 登录请求参数，包含登录类型和对应凭证
     * @return 登录响应 Mono，包含 JWT Token 对和用户信息
     */
    Mono<LoginResponse> login(LoginRequest request);

    /**
     * 获取当前策略对应的登录类型
     *
     * <p>每个策略实现通过该方法声明自己支持的登录类型，
     * {@link LoginStrategyProvider} 在初始化时通过遍历所有策略的 {@code getLoginType()}
     * 自动完成注册，无需手动维护映射关系。
     *
     * @return 当前策略对应的 {@link LoginType} 枚举值
     */
    LoginType getLoginType();
}
