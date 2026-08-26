package com.apex.scaffold.interfaces.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.annotation.ConcurrencyControl;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 并发控制切面单元测试
 *
 * <p>验证 ConcurrencyControlAspect 的并发控制逻辑，
 * 包括信号量获取、超时处理、异常中断等场景
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConcurrencyControlAspect 并发控制切面测试")
class ConcurrencyControlAspectTest {

    @InjectMocks
    private ConcurrencyControlAspect aspect;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private ConcurrencyControl annotation;

    private static final String METHOD_KEY = "ConcurrencyControlAspectTest.testMethod()";

    /**
     * 设置{@code up}。
     * @throws Throwable 当操作无法完成时
     */
    @BeforeEach
    void setUp() throws Throwable {
        when(annotation.maxConcurrent()).thenReturn(1);
        when(annotation.timeout()).thenReturn(5000L);
        when(annotation.message()).thenReturn("系统繁忙，请稍后再试");
    }

    /**
     * 执行 {@code around_whenPermitAcquired_shouldProceed} 操作。
     * @throws Throwable 当操作无法完成时
     */
    @Test
    @DisplayName("并发控制通过时应正常执行方法")
    void around_whenPermitAcquired_shouldProceed() throws Throwable {
        Signature signature = org.mockito.Mockito.mock(Signature.class);
        when(signature.toString()).thenReturn(METHOD_KEY);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.proceed()).thenReturn("success");

        Object result = aspect.around(joinPoint, annotation);

        assertThat(result).isEqualTo("success");
    }
}
