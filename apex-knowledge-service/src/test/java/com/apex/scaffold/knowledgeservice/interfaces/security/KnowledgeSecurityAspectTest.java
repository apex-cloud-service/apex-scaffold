package com.apex.scaffold.knowledgeservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.annotation.RequirePermission;
import java.lang.reflect.Method;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 知识库权限切面单元测试：权限码 OR/AND 匹配与 fail-closed 拒绝。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeSecurityAspectTest {

    @Mock
    private KnowledgePermissionClient permissionClient;

    @Mock
    private KnowledgeAuditLogServiceImpl auditService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    /**
     * 切面单测用的注解方法样例。
     *
     * @author Apex Team
     * @since 1.3.2
     */
    static class SampleHandler {

        /**
         * 单权限码方法。
         *
         * @return 固定值
         */
        @RequirePermission("kb:space:create")
        public Mono<String> create() {
            return Mono.just("ok");
        }
    }

    /**
     * 构建切面。
     *
     * @return 切面实例
     */
    private KnowledgeSecurityAspect aspect() {
        return new KnowledgeSecurityAspect(permissionClient, auditService);
    }

    /**
     * 构造携带样例方法与已认证用户的连接点。
     *
     * @param methodName 样例方法名
     * @throws Exception 方法反射失败
     */
    private void stubJoinPoint(String methodName) throws Throwable {
        Method method = SampleHandler.class.getMethod(methodName);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        // getArgs 仅在 @OperationLog 记录路径调用；本样例方法无该注解，不预先打桩
        org.mockito.Mockito.lenient().doReturn(Mono.just("ok")).when(joinPoint).proceed();
    }

    /**
     * 构造已认证用户。
     *
     * @return 用户主体
     */
    private AuthenticatedUser user() {
        return new AuthenticatedUser(1L, "t1");
    }

    /**
     * 构造携带已认证用户的 Reactor 安全上下文（切面身份来源）。
     *
     * @return 安全上下文
     */
    private reactor.util.context.Context securityContext() {
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                user(), null, List.of());
        return org.springframework.security.core.context.ReactiveSecurityContextHolder.withAuthentication(
                authentication);
    }

    @Test
    void permissionMatchAllowsInvocation() throws Throwable {
        stubJoinPoint("create");
        when(permissionClient.permissions(any(AuthenticatedUser.class)))
                .thenReturn(Mono.just(List.of("kb:space:create", "kb:space:read")));

        Object result = aspect().intercept(joinPoint);

        StepVerifier.create(((Mono<String>) result).contextWrite(securityContext()))
                .expectNext("ok")
                .verifyComplete();
    }

    @Test
    void missingPermissionIsDenied() throws Throwable {
        stubJoinPoint("create");
        when(permissionClient.permissions(any(AuthenticatedUser.class))).thenReturn(Mono.just(List.of("kb:doc:read")));

        Object result = aspect().intercept(joinPoint);

        StepVerifier.create(((Mono<String>) result).contextWrite(securityContext()))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void authorizationFailureIsDenied() throws Throwable {
        stubJoinPoint("create");
        // KnowledgePermissionClient 契约：任何查询失败均以 AccessDeniedException 对外传播
        when(permissionClient.permissions(any(AuthenticatedUser.class)))
                .thenReturn(Mono.error(new AccessDeniedException("Forbidden")));

        Object result = aspect().intercept(joinPoint);

        StepVerifier.create(((Mono<String>) result).contextWrite(securityContext()))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void nonMonoReturnTypeIsRejected() throws Exception {
        Method method = String.class.getMethod("toString");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                        AccessDeniedException.class, () -> aspect().intercept(joinPoint)))
                .isNotNull();
    }
}
