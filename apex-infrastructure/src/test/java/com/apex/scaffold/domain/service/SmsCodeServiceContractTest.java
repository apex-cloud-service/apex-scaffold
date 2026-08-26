package com.apex.scaffold.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SmsCodeService} 接口契约测试
 *
 * <p>验证 {@link SmsCodeService} 领域接口保持稳定的最小契约。
 *
 * <h2>测试目标</h2>
 * <ul>
 *   <li>验证 {@link SmsCodeService} 接口定义了 {@code sendAndStore} 和 {@code validate} 两个方法</li>
 *   <li>验证接口只暴露领域层需要的方法签名</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SmsCodeService
 */
@DisplayName("SmsCodeService 接口契约测试")
class SmsCodeServiceContractTest {

    /**
     * 验证 SmsCodeService 接口定义了 sendAndStore 和 validate 两个核心方法
     *
     * <p>这是统一接口的核心契约，所有实现必须提供这两个方法。
     */
    @Test
    @DisplayName("SmsCodeService 接口应定义 sendAndStore 和 validate 方法")
    void smsCodeServiceShouldDefineRequiredMethods() {
        Method[] methods = SmsCodeService.class.getDeclaredMethods();
        Set<String> methodNames = Arrays.stream(methods).map(Method::getName).collect(Collectors.toSet());

        assertThat(methodNames).as("SmsCodeService 接口应包含 sendAndStore 方法").contains("sendAndStore");

        assertThat(methodNames).as("SmsCodeService 接口应包含 validate 方法").contains("validate");

        assertThat(methods.length)
                .as("SmsCodeService 接口应只定义 2 个方法（sendAndStore 和 validate）")
                .isEqualTo(2);
    }
}
