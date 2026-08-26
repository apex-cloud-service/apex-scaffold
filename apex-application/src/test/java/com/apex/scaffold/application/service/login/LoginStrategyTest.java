package com.apex.scaffold.application.service.login;

import static org.assertj.core.api.Assertions.*;

import com.apex.scaffold.application.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * 登录策略接口单元测试类
 *
 * <p>测试 {@link LoginStrategy} 接口的方法签名和契约规范，验证所有实现类遵循统一的
 * 登录操作定义。由于 LoginStrategy 是接口，本测试主要验证：
 * <ul>
 *   <li>login 方法返回 {@code Mono<LoginResponse>} 类型</li>
 *   <li>login 方法接受 {@link LoginRequest} 参数</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see LoginStrategy
 * @see WeChatCodeLoginStrategy
 * @see WeChatPhoneLoginStrategy
 * @see SmsLoginStrategy
 * @see ChuanglanPhoneLoginStrategy
 * @see ChuanglanSmsLoginStrategy
 */
class LoginStrategyTest {

    /**
     * 执行 {@code allImplementations_ImplementLoginStrategy} 操作。
     */
    @Test
    @DisplayName("所有实现类应实现LoginStrategy接口 - 验证接口契约")
    void allImplementations_ImplementLoginStrategy() {
        assertThat(WeChatCodeLoginStrategy.class).isAssignableTo(LoginStrategy.class);
        assertThat(WeChatPhoneLoginStrategy.class).isAssignableTo(LoginStrategy.class);
        assertThat(SmsLoginStrategy.class).isAssignableTo(LoginStrategy.class);
        assertThat(ChuanglanPhoneLoginStrategy.class).isAssignableTo(LoginStrategy.class);
        assertThat(ChuanglanSmsLoginStrategy.class).isAssignableTo(LoginStrategy.class);
    }

    /**
     * 执行 {@code loginMethod_ReturnsMonoLoginResponse} 操作。
     * @throws NoSuchMethodException 当操作无法完成时
     */
    @Test
    @DisplayName("login方法签名 - 返回Mono<LoginResponse>")
    void loginMethod_ReturnsMonoLoginResponse() throws NoSuchMethodException {
        var method = LoginStrategy.class.getMethod("login", LoginRequest.class);
        assertThat(method.getReturnType()).isEqualTo(Mono.class);
    }

    /**
     * 执行 {@code loginMethod_AcceptsLoginRequest} 操作。
     * @throws NoSuchMethodException 当操作无法完成时
     */
    @Test
    @DisplayName("login方法签名 - 接收LoginRequest参数")
    void loginMethod_AcceptsLoginRequest() throws NoSuchMethodException {
        var method = LoginStrategy.class.getMethod("login", LoginRequest.class);
        assertThat(method.getParameterCount()).isEqualTo(1);
        assertThat(method.getParameterTypes()[0]).isEqualTo(LoginRequest.class);
    }
}
