package com.apex.scaffold.integrationservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.audit.AuditLogService;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 集成服务权限与审计切面测试：验证注解在实际执行链生效，而不只是元数据存在。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("集成服务权限与审计切面")
class IntegrationSecurityAspectTest {

    @Mock
    private IntegrationPermissionClient permissions;

    @Mock
    private AuditLogService audit;

    private IntegrationSecurityAspect aspect;

    private Method writeMethod;

    private Method readMethod;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        aspect = new IntegrationSecurityAspect(permissions, audit);
        writeMethod = Handler.class.getDeclaredMethod("write");
        readMethod = Handler.class.getDeclaredMethod("read");
    }

    @Test
    @DisplayName("授权通过后调用 Handler 并写入审计")
    void shouldAuthorizeAndAudit() {
        when(permissions.permissions(any())).thenReturn(Mono.just(List.of("integration:credential:manage")));
        when(audit.log(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(Mono.empty());
        Mono<Object> invocation = response();

        StepVerifier.create(asUser(aspect.execute(writeMethod, invocation)))
                .expectNextCount(1)
                .verifyComplete();

        verify(permissions).permissions(any());
        verify(audit).log(eq("7"), eq("t-1"), eq("INSERT"), anyString(), eq("HTTP_RESPONSE_READY"), anyMap());
    }

    @Test
    @DisplayName("缺少权限码时拒绝且不调用 Handler、不写审计")
    void shouldDenyWithoutPermission() {
        when(permissions.permissions(any())).thenReturn(Mono.just(List.of("iot:device:read")));
        Mono<Object> invocation = response();

        StepVerifier.create(asUser(aspect.execute(writeMethod, invocation)))
                .expectError(AccessDeniedException.class)
                .verify();

        verify(audit, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("无认证主体时拒绝")
    void shouldDenyWithoutPrincipal() {
        StepVerifier.create(aspect.execute(readMethod, response()))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    @DisplayName("只读方法记录审计但不要求权限")
    void shouldAuditReadWithoutPermissionAnnotation() {
        when(audit.log(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(Mono.empty());

        StepVerifier.create(asUser(aspect.execute(readMethod, response())))
                .expectNextCount(1)
                .verifyComplete();

        verify(permissions, never()).permissions(any());
        verify(audit).log(eq("7"), eq("t-1"), eq("SELECT"), anyString(), eq("HTTP_RESPONSE_READY"), anyMap());
    }

    /** 构造一个成功响应发布者作为 Handler 返回值。 */
    private static Mono<Object> response() {
        return ServerResponse.ok().build().cast(Object.class);
    }

    /**
     * 以可信认证主体绑定执行。
     *
     * @param publisher 待执行链
     * @return 绑定认证上下文后的链
     */
    private Mono<Object> asUser(Mono<Object> publisher) {
        var authentication =
                new UsernamePasswordAuthenticationToken(new AuthenticatedUser(7L, null, "t-1"), null, List.of());
        return publisher.contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    @Test
    @DisplayName("测试处理器注解与权限码保持不变")
    void handlerShouldDeclareExpectedAnnotations() {
        RequirePermission required = writeMethod.getAnnotation(RequirePermission.class);
        OperationLog operation = writeMethod.getAnnotation(OperationLog.class);
        assertThat(required.value()).isEqualTo("integration:credential:manage");
        assertThat(operation.type()).isEqualTo(OperationLog.OperationType.INSERT);
    }

    /** 测试用处理器：写方法带权限+审计，读方法仅审计。 */
    static class Handler {
        @RequirePermission("integration:credential:manage")
        @OperationLog(module = "integration", type = OperationLog.OperationType.INSERT, description = "写操作")
        Mono<ServerResponse> write() {
            return ServerResponse.ok().build();
        }

        @OperationLog(module = "integration", type = OperationLog.OperationType.SELECT, description = "读操作")
        Mono<ServerResponse> read() {
            return ServerResponse.ok().build();
        }
    }
}
