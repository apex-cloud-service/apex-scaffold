package com.apex.scaffold.authcenter.application.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.authcenter.domain.service.ResourcePermissionService;
import com.apex.scaffold.common.annotation.RequireResourcePermission;
import com.apex.scaffold.common.exception.BusinessException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResourcePermissionAspect 单元测试")
class ResourcePermissionAspectTest {

    @Mock
    private ResourcePermissionService permissionService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private Authentication authentication;

    @Mock
    private ServerWebExchange exchange;

    @Mock
    private ServerHttpRequest request;

    @InjectMocks
    private ResourcePermissionAspect aspect;

    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_RESOURCE_TYPE = "document";
    private static final String TEST_RESOURCE_ID = "doc-456";
    private static final String TEST_PERMISSION = "read";

    @BeforeEach
    void setUp() {
        lenient().when(authentication.getName()).thenReturn(TEST_USER_ID);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
    }

    @Test
    @DisplayName("权限检查成功 - 用户有权限")
    void testPermissionCheckSuccess() throws Throwable {
        Method method = TestController.class.getMethod("getDocument", String.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[] {TEST_RESOURCE_ID});
        when(joinPoint.proceed()).thenReturn(Mono.just("success"));

        Collection<GrantedAuthority> authorities = Collections.emptyList();
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        when(permissionService.hasPermission(
                        eq(TEST_USER_ID), eq(TEST_RESOURCE_TYPE), eq(TEST_RESOURCE_ID), eq(TEST_PERMISSION), any()))
                .thenReturn(Mono.just(true));

        SecurityContext securityContext = new SecurityContextImpl(authentication);

        Mono<String> result = Mono.defer(() -> {
                    try {
                        return (Mono<String>) aspect.checkResourcePermission(joinPoint);
                    } catch (Throwable e) {
                        return Mono.error(e);
                    }
                })
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result).expectNext("success").verifyComplete();
    }

    @Test
    @DisplayName("权限检查失败 - 用户无权限")
    void testPermissionCheckFailed() throws Throwable {
        Method method = TestController.class.getMethod("getDocument", String.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[] {TEST_RESOURCE_ID});

        Collection<GrantedAuthority> authorities = Collections.emptyList();
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        when(permissionService.hasPermission(
                        eq(TEST_USER_ID), eq(TEST_RESOURCE_TYPE), eq(TEST_RESOURCE_ID), eq(TEST_PERMISSION), any()))
                .thenReturn(Mono.just(false));

        SecurityContext securityContext = new SecurityContextImpl(authentication);

        Mono<?> result = Mono.defer(() -> {
                    try {
                        return (Mono<?>) aspect.checkResourcePermission(joinPoint);
                    } catch (Throwable e) {
                        return Mono.error(e);
                    }
                })
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof BusinessException && ((BusinessException) throwable).getCode() == 403)
                .verify();
    }

    @Test
    @DisplayName("权限检查失败 - 未认证")
    void testPermissionCheckNotAuthenticated() throws Throwable {
        Method method = TestController.class.getMethod("getDocument", String.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[] {TEST_RESOURCE_ID});

        when(authentication.isAuthenticated()).thenReturn(false);

        SecurityContext securityContext = new SecurityContextImpl(authentication);

        Mono<?> result = Mono.defer(() -> {
                    try {
                        return (Mono<?>) aspect.checkResourcePermission(joinPoint);
                    } catch (Throwable e) {
                        return Mono.error(e);
                    }
                })
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .expectErrorSatisfies(throwable -> {
                    assertThat(throwable).isInstanceOf(BusinessException.class);
                    BusinessException e = (BusinessException) throwable;
                    assertThat(e.getCode()).isEqualTo(401);
                })
                .verify();
    }

    @Test
    @DisplayName("写操作权限检查成功")
    void testWritePermissionCheckSuccess() throws Throwable {
        Method method = TestController.class.getMethod("updateDocument", String.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[] {TEST_RESOURCE_ID});
        when(joinPoint.proceed()).thenReturn(Mono.just("updated"));

        Collection<GrantedAuthority> authorities = Collections.emptyList();
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        when(permissionService.hasPermission(
                        eq(TEST_USER_ID), eq(TEST_RESOURCE_TYPE), eq(TEST_RESOURCE_ID), eq("write"), any()))
                .thenReturn(Mono.just(true));

        SecurityContext securityContext = new SecurityContextImpl(authentication);

        Mono<String> result = Mono.defer(() -> {
                    try {
                        return (Mono<String>) aspect.checkResourcePermission(joinPoint);
                    } catch (Throwable e) {
                        return Mono.error(e);
                    }
                })
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result).expectNext("updated").verifyComplete();
    }

    @Test
    @DisplayName("超级管理员权限检查成功")
    void testSuperAdminPermissionCheck() throws Throwable {
        Method method = TestController.class.getMethod("getDocument", String.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[] {TEST_RESOURCE_ID});
        when(joinPoint.proceed()).thenReturn(Mono.just("success"));

        Collection<GrantedAuthority> authorities =
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        when(permissionService.hasPermission(
                        eq(TEST_USER_ID),
                        eq(TEST_RESOURCE_TYPE),
                        eq(TEST_RESOURCE_ID),
                        eq(TEST_PERMISSION),
                        argThat(ctx -> ctx.isSuperAdmin())))
                .thenReturn(Mono.just(true));

        SecurityContext securityContext = new SecurityContextImpl(authentication);

        Mono<String> result = Mono.defer(() -> {
                    try {
                        return (Mono<String>) aspect.checkResourcePermission(joinPoint);
                    } catch (Throwable e) {
                        return Mono.error(e);
                    }
                })
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result).expectNext("success").verifyComplete();
    }

    @Test
    @DisplayName("提取客户端IP - X-Real-IP")
    void testExtractClientIpXRealIp() throws Throwable {
        Method method =
                TestController.class.getMethod("getDocumentWithExchange", String.class, ServerWebExchange.class);

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Real-IP", "192.168.1.100");

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[] {TEST_RESOURCE_ID, exchange});
        when(joinPoint.proceed()).thenReturn(Mono.just("success"));

        Collection<GrantedAuthority> authorities = Collections.emptyList();
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        when(permissionService.hasPermission(
                        eq(TEST_USER_ID),
                        eq(TEST_RESOURCE_TYPE),
                        eq(TEST_RESOURCE_ID),
                        eq(TEST_PERMISSION),
                        argThat(ctx -> "192.168.1.100".equals(ctx.getClientIp()))))
                .thenReturn(Mono.just(true));

        SecurityContext securityContext = new SecurityContextImpl(authentication);

        Mono<String> result = Mono.defer(() -> {
                    try {
                        return (Mono<String>) aspect.checkResourcePermission(joinPoint);
                    } catch (Throwable e) {
                        return Mono.error(e);
                    }
                })
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result).expectNext("success").verifyComplete();
    }

    @Test
    @DisplayName("提取设备ID - X-Device-ID")
    void testExtractDeviceId() throws Throwable {
        Method method =
                TestController.class.getMethod("getDocumentWithExchange", String.class, ServerWebExchange.class);

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Device-ID", "device-abc-123");

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[] {TEST_RESOURCE_ID, exchange});
        when(joinPoint.proceed()).thenReturn(Mono.just("success"));

        Collection<GrantedAuthority> authorities = Collections.emptyList();
        when(authentication.getAuthorities()).thenReturn((Collection) authorities);

        when(permissionService.hasPermission(
                        eq(TEST_USER_ID),
                        eq(TEST_RESOURCE_TYPE),
                        eq(TEST_RESOURCE_ID),
                        eq(TEST_PERMISSION),
                        argThat(ctx -> "device-abc-123".equals(ctx.getDeviceId()))))
                .thenReturn(Mono.just(true));

        SecurityContext securityContext = new SecurityContextImpl(authentication);

        Mono<String> result = Mono.defer(() -> {
                    try {
                        return (Mono<String>) aspect.checkResourcePermission(joinPoint);
                    } catch (Throwable e) {
                        return Mono.error(e);
                    }
                })
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result).expectNext("success").verifyComplete();
    }

    static class TestController {
        @RequireResourcePermission(
                resourceType = TEST_RESOURCE_TYPE,
                resourceIdParam = "documentId",
                permission = TEST_PERMISSION)
        public Mono<String> getDocument(String documentId) {
            return Mono.just("document");
        }

        @RequireResourcePermission(
                resourceType = TEST_RESOURCE_TYPE,
                resourceIdParam = "documentId",
                permission = "write")
        public Mono<String> updateDocument(String documentId) {
            return Mono.just("updated");
        }

        @RequireResourcePermission(
                resourceType = TEST_RESOURCE_TYPE,
                resourceIdParam = "documentId",
                permission = TEST_PERMISSION)
        public Mono<String> getDocumentWithExchange(String documentId, ServerWebExchange exchange) {
            return Mono.just("document");
        }
    }
}
