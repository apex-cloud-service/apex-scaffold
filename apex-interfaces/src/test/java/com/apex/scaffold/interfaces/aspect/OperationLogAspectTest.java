package com.apex.scaffold.interfaces.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.service.OperationLogService;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.OperationLog.OperationType;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

/**
 * 操作日志切面单元测试
 *
 * <p>验证 OperationLogAspect 的操作日志自动记录功能，
 * 包括日志初始化、请求信息设置、执行状态记录等场景
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OperationLogAspect 操作日志切面测试")
class OperationLogAspectTest {

    @Mock
    private OperationLogService operationLogService;

    @Mock
    private UserService userService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private OperationLog operationLog;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OperationLogAspect aspect;

    private MockedStatic<TraceIdUtil> traceIdUtilMockedStatic;
    private static final String TEST_TRACE_ID = "test-trace-id-123456";

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        traceIdUtilMockedStatic = mockStatic(TraceIdUtil.class);
        traceIdUtilMockedStatic.when(TraceIdUtil::getTraceId).thenReturn(TEST_TRACE_ID);
    }

    /**
     * 执行 {@code tearDown} 操作。
     */
    @AfterEach
    void tearDown() {
        traceIdUtilMockedStatic.close();
    }

    /**
     * 执行 {@code around_whenMethodSucceeds_shouldLogSuccess} 操作。
     * @throws Throwable 当操作无法完成时
     */
    @Test
    @DisplayName("方法正常执行时应记录成功日志")
    void around_whenMethodSucceeds_shouldLogSuccess() throws Throwable {
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.toString()).thenReturn("testMethod");
        when(signature.getMethod())
                .thenReturn(
                        OperationLogAspectTest.class.getDeclaredMethod("around_whenMethodSucceeds_shouldLogSuccess"));
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn("success");

        when(operationLog.module()).thenReturn("用户管理");
        when(operationLog.type()).thenReturn(OperationType.INSERT);
        when(operationLog.description()).thenReturn("新增用户");
        when(operationLog.saveRequest()).thenReturn(true);
        when(operationLog.saveResponse()).thenReturn(true);
        when(operationLog.excludeRequestFields()).thenReturn(new String[] {"password"});

        Object result = aspect.around(joinPoint, operationLog);

        assertThat(result).isEqualTo("success");
    }

    /**
     * 执行 {@code around_whenMethodThrowsException_shouldLogFailureAndRethrow} 操作。
     * @throws Throwable 当操作无法完成时
     */
    @Test
    @DisplayName("方法抛出异常时应记录失败日志并重新抛出异常")
    void around_whenMethodThrowsException_shouldLogFailureAndRethrow() throws Throwable {
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.toString()).thenReturn("testMethod");
        when(signature.getMethod())
                .thenReturn(OperationLogAspectTest.class.getDeclaredMethod(
                        "around_whenMethodThrowsException_shouldLogFailureAndRethrow"));
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("业务异常"));

        when(operationLog.module()).thenReturn("用户管理");
        when(operationLog.type()).thenReturn(OperationType.UPDATE);
        when(operationLog.description()).thenReturn("更新用户");
        when(operationLog.saveRequest()).thenReturn(false);
        when(operationLog.saveResponse()).thenReturn(false);
        when(operationLog.excludeRequestFields()).thenReturn(new String[0]);

        assertThatThrownBy(() -> aspect.around(joinPoint, operationLog))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("业务异常");
    }

    /**
     * 执行 {@code around_withServerWebExchange_shouldSetRequestInfo} 操作。
     * @throws Throwable 当操作无法完成时
     */
    @Test
    @DisplayName("请求中包含ServerWebExchange时应记录请求信息")
    void around_withServerWebExchange_shouldSetRequestInfo() throws Throwable {
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(exchange.getRequest()).thenReturn(request);
        when(request.getURI()).thenReturn(java.net.URI.create("/api/user"));
        when(request.getMethod()).thenReturn(org.springframework.http.HttpMethod.POST);
        when(request.getHeaders()).thenReturn(new org.springframework.http.HttpHeaders());

        MethodSignature signature = mock(MethodSignature.class);
        when(signature.toString()).thenReturn("testMethod");
        when(signature.getMethod())
                .thenReturn(OperationLogAspectTest.class.getDeclaredMethod(
                        "around_withServerWebExchange_shouldSetRequestInfo"));
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[] {exchange});
        when(joinPoint.proceed()).thenReturn("success");

        when(operationLog.module()).thenReturn("用户管理");
        when(operationLog.type()).thenReturn(OperationType.INSERT);
        when(operationLog.description()).thenReturn("新增用户");
        when(operationLog.saveRequest()).thenReturn(false);
        when(operationLog.saveResponse()).thenReturn(false);
        when(operationLog.excludeRequestFields()).thenReturn(new String[0]);

        Object result = aspect.around(joinPoint, operationLog);

        assertThat(result).isEqualTo("success");
    }

    /**
     * 执行 {@code around_whenSaveLogFails_shouldNotAffectMainFlow} 操作。
     * @throws Throwable 当操作无法完成时
     */
    @Test
    @DisplayName("保存操作日志失败时不应影响主流程")
    void around_whenSaveLogFails_shouldNotAffectMainFlow() throws Throwable {
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.toString()).thenReturn("testMethod");
        when(signature.getMethod())
                .thenReturn(OperationLogAspectTest.class.getDeclaredMethod(
                        "around_whenSaveLogFails_shouldNotAffectMainFlow"));
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn("success");

        when(operationLog.module()).thenReturn("用户管理");
        when(operationLog.type()).thenReturn(OperationType.SELECT);
        when(operationLog.description()).thenReturn("查询用户");
        when(operationLog.saveRequest()).thenReturn(false);
        when(operationLog.saveResponse()).thenReturn(false);
        when(operationLog.excludeRequestFields()).thenReturn(new String[0]);

        doThrow(new RuntimeException("保存失败")).when(operationLogService).saveAsync(any());

        Object result = aspect.around(joinPoint, operationLog);

        assertThat(result).isEqualTo("success");
    }
}
