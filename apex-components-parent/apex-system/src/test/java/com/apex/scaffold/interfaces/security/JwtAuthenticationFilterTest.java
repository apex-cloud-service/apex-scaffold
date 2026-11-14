package com.apex.scaffold.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.domain.model.entity.RoleEntity;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.RoleRepository;
import com.apex.scaffold.domain.repository.UserRepository;
import com.apex.scaffold.domain.service.TokenBlacklistService;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * JWT认证过滤器单元测试
 *
 * <p>验证 JwtAuthenticationFilter 的认证逻辑，包括公开路径跳过、
 * Token提取、黑名单校验、Token解析、异常处理等场景
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtAuthenticationFilter JWT认证过滤器测试")
class JwtAuthenticationFilterTest {

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private WebFilterChain chain;

    @Mock
    private MessageUtils messageUtils;

    @Mock
    private TokenVerifier tokenVerifier;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private static final String VALID_TOKEN = "valid-token-value";
    private static final String TEST_OPEN_ID = "test-open-id";

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        when(messageUtils.get(any(Locale.class), any())).thenReturn("");
    }

    @Test
    void centralAuthoritiesIgnoreRequestTenantAndLocalRoles() {
        com.apex.scaffold.domain.gateway.UnifiedAuthorizationGateway gateway =
                org.mockito.Mockito.mock(com.apex.scaffold.domain.gateway.UnifiedAuthorizationGateway.class);
        com.apex.scaffold.application.service.UnifiedAuthorizationService unified =
                new com.apex.scaffold.application.service.UnifiedAuthorizationService(
                        gateway,
                        new org.springframework.mock.env.MockEnvironment()
                                .withProperty("apex.features.auth-center-permission", "true"),
                        null);
        JwtAuthenticationFilter central = new JwtAuthenticationFilter(
                tokenBlacklistService, null, messageUtils, userRepository, roleRepository, unified, tokenVerifier);
        UserEntity user = UserEntity.create(TEST_OPEN_ID, "reader");
        user.setId(7L);
        user.setTenantId("main");
        when(userRepository.findByOpenId(TEST_OPEN_ID)).thenReturn(Mono.just(user));
        when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
        when(gateway.query("main", 7L))
                .thenReturn(Mono.just(new com.apex.scaffold.domain.gateway.UnifiedAuthorizationGateway.Authorization(
                        "main",
                        7L,
                        java.util.List.of(),
                        java.util.List.of(new com.apex.scaffold.domain.gateway.UnifiedAuthorizationGateway.Role(
                                "AUDITOR", "SELF")))));
        Claims centralClaims = mockClaims();
        when(tokenVerifier.parse(VALID_TOKEN)).thenReturn(centralClaims);
        when(chain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation ->
                        org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
                                .switchIfEmpty(Mono.error(new AssertionError("Missing authentication")))
                                .doOnNext(context -> assertThat(
                                                context.getAuthentication().getAuthorities())
                                        .extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
                                        .containsExactly("ROLE_AUDITOR"))
                                .then(Mono.deferContextual(context -> {
                                    assertThat(context.<String>get(
                                                    com.apex.scaffold.common.context.TenantContext.REACTOR_CONTEXT_KEY))
                                            .isEqualTo("main");
                                    return Mono.empty();
                                })));
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/internal/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .header("X-Tenant-Id", "foreign")
                .build());
        StepVerifier.create(central.filter(exchange, chain)).verifyComplete();
        verifyNoInteractions(roleRepository);
        verify(gateway).query("main", 7L);
    }

    @Test
    void centralFailureReturnsUnavailableWithoutLocalFallback() {
        com.apex.scaffold.application.service.UnifiedAuthorizationService unified =
                org.mockito.Mockito.mock(com.apex.scaffold.application.service.UnifiedAuthorizationService.class);
        when(unified.enabled()).thenReturn(true);
        when(unified.roles(7L)).thenReturn(Mono.error(new IllegalStateException("Unavailable")));
        JwtAuthenticationFilter central = new JwtAuthenticationFilter(
                tokenBlacklistService, null, messageUtils, userRepository, roleRepository, unified, tokenVerifier);
        UserEntity user = UserEntity.create(TEST_OPEN_ID, "reader");
        user.setId(7L);
        user.setTenantId("main");
        when(userRepository.findByOpenId(TEST_OPEN_ID)).thenReturn(Mono.just(user));
        when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
        Claims centralClaims = mockClaims();
        when(tokenVerifier.parse(VALID_TOKEN)).thenReturn(centralClaims);
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/internal/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .build());
        StepVerifier.create(central.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatusCode.valueOf(503));
        verifyNoInteractions(roleRepository, chain);
    }

    @Test
    @DisplayName("普通业务接口未提供Token应返回401")
    void filter_withProjectV1ApiWithoutToken_shouldReturnUnauthorized() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/user/profile").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
        verifyNoInteractions(tokenBlacklistService, chain);
    }

    /**
     * 验证建立登录态所需的接口仍可匿名调用。
     *
     * <p>登录与短信验证码接口必须在用户尚未取得 JWT 时可访问，但公共范围只允许
     * 精确路径，不能重新引入覆盖全部业务接口的通配规则。</p>
     */
    @Test
    @DisplayName("登录前认证接口未提供Token应直接放行")
    void filter_withPreAuthenticationApiWithoutToken_shouldSkipAuthentication() {
        MockServerHttpRequest request =
                MockServerHttpRequest.post("/api/v1/auth/login").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
        verifyNoInteractions(tokenBlacklistService);
    }

    /**
     * 验证投票照片静态资源不需要 JWT。
     *
     * <p>二选一和四选二接口返回的照片地址以 {@code /uploads/apex/} 开头，浏览器会直接
     * 使用该地址加载图片，不会额外携带业务接口的 Authorization 请求头。因此该路径必须
     * 在自定义 JWT 过滤器中跳过认证；否则即使服务器磁盘上的图片真实存在，也会在读取文件
     * 之前被拦截并返回 401。</p>
     */
    @Test
    @DisplayName("AF和AM投票照片未携带Token时应跳过JWT认证")
    void filter_withVotePhotoWithoutToken_shouldSkipAuthentication() {
        MockServerHttpRequest femalePhotoRequest =
                MockServerHttpRequest.get("/uploads/apex/AF/female-photo.jpg").build();
        MockServerHttpRequest malePhotoRequest =
                MockServerHttpRequest.get("/uploads/apex/AM/male-photo.jpg").build();

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(MockServerWebExchange.from(femalePhotoRequest), chain))
                .verifyComplete();
        StepVerifier.create(filter.filter(MockServerWebExchange.from(malePhotoRequest), chain))
                .verifyComplete();

        verify(chain, org.mockito.Mockito.times(2)).filter(any(ServerWebExchange.class));
        verifyNoInteractions(tokenBlacklistService);
    }

    /**
     * 验证两个投票对局接口不会被公共路径放行。
     *
     * <p>它们必须解析 JWT 取得当前用户 openId；未携带 Token 时应明确返回 401，
     * 不能再返回没有响应体的 200。</p>
     */
    @Test
    @DisplayName("二选一和四选二未携带Token时应返回401")
    void filter_withVoteBattleApiWithoutToken_shouldReturnUnauthorized() {
        ServerWebExchange choice2Exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v2/explore/choice-2/current").build());
        ServerWebExchange fourPickTwoExchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/v2/explore/four-pick-two/current")
                        .build());

        StepVerifier.create(filter.filter(choice2Exchange, chain)).verifyComplete();
        StepVerifier.create(filter.filter(fourPickTwoExchange, chain)).verifyComplete();

        assertThat(choice2Exchange.getResponse().getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
        assertThat(fourPickTwoExchange.getResponse().getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
        verifyNoInteractions(tokenBlacklistService, chain);
    }

    @Test
    @DisplayName("未提供Token应返回401错误")
    void filter_withoutToken_shouldReturnUnauthorized() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/internal/user/profile").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
    }

    @Test
    @DisplayName("Token在黑名单中应返回401错误")
    void filter_withBlacklistedToken_shouldReturnUnauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(true));

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
    }

    @Test
    @DisplayName("有效Token应通过认证并设置SecurityContext")
    void filter_withValidToken_shouldAuthenticate() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        Claims claims = mockClaims();
        UserEntity user = UserEntity.create(TEST_OPEN_ID, "测试用户");
        user.setId(1L);

        when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
        when(userRepository.findByOpenId(TEST_OPEN_ID)).thenReturn(Mono.just(user));
        when(roleRepository.findByUserId(user.getId())).thenReturn(Mono.just(java.util.List.of()));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        when(tokenVerifier.parse(VALID_TOKEN)).thenReturn(claims);

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
    }

    @Test
    @DisplayName("有效Token认证后SecurityContext应提供可信租户")
    void filter_withTenantUser_shouldExposeTrustedTenantInSecurityContext() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        Claims claims = mockClaims();
        UserEntity user = UserEntity.create(TEST_OPEN_ID, "租户用户");
        user.setId(2L);
        user.setTenantId("tenant-a");
        user.setDeptId(20L);

        when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
        when(userRepository.findByOpenId(TEST_OPEN_ID)).thenReturn(Mono.just(user));
        when(roleRepository.findByUserId(user.getId())).thenReturn(Mono.just(java.util.List.of()));
        when(chain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation ->
                        org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
                                .switchIfEmpty(Mono.error(new AssertionError("SecurityContext missing")))
                                .doOnNext(securityContext -> {
                                    Object principal =
                                            securityContext.getAuthentication().getPrincipal();
                                    assertThat(principal).isInstanceOf(AuthenticatedUser.class);
                                    AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
                                    assertThat(authenticatedUser.tenantId()).isEqualTo("tenant-a");
                                    assertThat(authenticatedUser.deptId()).isEqualTo(20L);
                                    assertThat(authenticatedUser.openId()).isEqualTo(TEST_OPEN_ID);
                                    Object exchangeAuthenticatedUser = exchange.getAttribute("apex.authenticatedUser");
                                    assertThat(exchangeAuthenticatedUser).isEqualTo(authenticatedUser);
                                    Object exchangeDeptId = exchange.getAttribute("apex.deptId");
                                    assertThat(exchangeDeptId).isEqualTo(20L);
                                    assertThat(securityContext
                                                    .getAuthentication()
                                                    .getName())
                                            .isEqualTo(TEST_OPEN_ID);
                                    assertThat(principal)
                                            .hasToString(
                                                    "AuthenticatedUser[userId=2, openId=test-open-id, tenantId=tenant-a]");
                                })
                                .then());
        when(tokenVerifier.parse(VALID_TOKEN)).thenReturn(claims);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }

    /**
     * 验证数据库角色已带 ROLE_ 前缀时不再叠加前缀，SecurityContext 中的权限
     * 只出现一次且保持原始值。
     */
    @Test
    @DisplayName("数据库角色已带ROLE前缀时不得生成ROLE_ROLE前缀")
    void filter_withPrefixedRoleCode_shouldNormalizeAuthorityOnce() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/admin-api/v1/photo-reviews")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        Claims claims = mockClaims();
        UserEntity user = UserEntity.create(TEST_OPEN_ID, "审核员");
        user.setId(9L);
        RoleEntity role = RoleEntity.create("照片审核员", "ROLE_PHOTO_REVIEWER", "test");

        when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
        when(userRepository.findByOpenId(TEST_OPEN_ID)).thenReturn(Mono.just(user));
        when(roleRepository.findByUserId(9L)).thenReturn(Mono.just(java.util.List.of(role)));
        when(chain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation ->
                        org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
                                .switchIfEmpty(Mono.error(new AssertionError("SecurityContext missing")))
                                .doOnNext(securityContext -> assertThat(securityContext
                                                .getAuthentication()
                                                .getAuthorities())
                                        .extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
                                        .containsExactly("ROLE_PHOTO_REVIEWER"))
                                .then());
        when(tokenVerifier.parse(VALID_TOKEN)).thenReturn(claims);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }

    @Test
    @DisplayName("角色显示名称包含admin时不得授予管理员权限")
    void filter_withAdminOnlyInRoleName_shouldNotGrantAdminAuthority() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/admin-api/v1/photo-reviews")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        Claims claims = mockClaims();
        UserEntity user = UserEntity.create(TEST_OPEN_ID, "审计员");
        user.setId(11L);
        RoleEntity role = RoleEntity.create("admin assistant", "AUDITOR", "test");

        when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
        when(userRepository.findByOpenId(TEST_OPEN_ID)).thenReturn(Mono.just(user));
        when(roleRepository.findByUserId(11L)).thenReturn(Mono.just(java.util.List.of(role)));
        when(chain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation ->
                        org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
                                .switchIfEmpty(Mono.error(new AssertionError("SecurityContext missing")))
                                .doOnNext(securityContext -> assertThat(securityContext
                                                .getAuthentication()
                                                .getAuthorities())
                                        .extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
                                        .containsExactly("ROLE_AUDITOR"))
                                .then());
        when(tokenVerifier.parse(VALID_TOKEN)).thenReturn(claims);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }

    /**
     * 验证兴趣治理路径每次实时读取角色（绕过角色缓存），撤权可即时生效。
     */
    @Test
    @DisplayName("兴趣治理请求每次实时读取角色以便撤权立即生效")
    void filter_withInterestGovernancePath_shouldBypassRoleCache() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/v1/interests/graphs/2/readiness")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .build();
        Claims claims = mockClaims();
        UserEntity user = UserEntity.create(TEST_OPEN_ID, "图谱编辑");
        user.setId(10L);
        RoleEntity editor = RoleEntity.create("图谱编辑", "INTEREST_GRAPH_EDITOR", "test");
        when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
        when(userRepository.findByOpenId(TEST_OPEN_ID)).thenReturn(Mono.just(user));
        when(roleRepository.findByUserId(10L))
                .thenReturn(Mono.just(java.util.List.of(editor)), Mono.just(java.util.List.of()));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        when(tokenVerifier.parse(VALID_TOKEN)).thenReturn(claims);

        StepVerifier.create(filter.filter(MockServerWebExchange.from(request), chain))
                .verifyComplete();
        StepVerifier.create(filter.filter(MockServerWebExchange.from(request), chain))
                .verifyComplete();

        verify(roleRepository, org.mockito.Mockito.times(2)).findByUserId(10L);
    }

    @Test
    @DisplayName("RefreshToken不得访问业务接口")
    void filter_withRefreshToken_shouldReturnUnauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        Claims claims = mockClaims();
        when(claims.get("type", String.class)).thenReturn("refresh");
        when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
        when(tokenVerifier.parse(VALID_TOKEN)).thenReturn(claims);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
        verifyNoInteractions(userRepository, chain);
    }

    @Test
    @DisplayName("Token对应用户不存在时应拒绝认证")
    void filter_withMissingUser_shouldReturnUnauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        Claims claims = mockClaims();
        when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
        when(userRepository.findByOpenId(TEST_OPEN_ID)).thenReturn(Mono.empty());
        when(tokenVerifier.parse(VALID_TOKEN)).thenReturn(claims);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
        verifyNoInteractions(chain);
    }

    /**
     * 验证注销后旧 Token 因 subject 撤销立即失效（401），同一 openId 重注册的
     * 新账号携带新 Token 仍可正常认证并放行到下游。
     */
    @Test
    @DisplayName("注销后旧Token立即失效，随后同openId重注册的新账号仍可认证")
    void filter_withDeletedIdentityThenReregisteredIdentity() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .build();
        Claims oldClaims = mockClaims();
        Claims newClaims = mockClaims();
        when(oldClaims.getIssuedAt()).thenReturn(new Date(1_000L));
        when(newClaims.getIssuedAt()).thenReturn(new Date(2_000L));
        UserEntity reRegisteredUser = UserEntity.create(TEST_OPEN_ID, "重注册用户");
        reRegisteredUser.setId(2L);

        when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
        when(tokenBlacklistService.isSubjectRevoked(
                        org.mockito.ArgumentMatchers.eq(TEST_OPEN_ID), org.mockito.ArgumentMatchers.any(Date.class)))
                .thenReturn(Mono.just(true), Mono.just(false));
        when(userRepository.findByOpenId(TEST_OPEN_ID)).thenReturn(Mono.just(reRegisteredUser));
        when(roleRepository.findByUserId(2L)).thenReturn(Mono.just(java.util.List.of()));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        when(tokenVerifier.parse(VALID_TOKEN)).thenReturn(oldClaims, newClaims);

        ServerWebExchange oldExchange = MockServerWebExchange.from(request);
        StepVerifier.create(filter.filter(oldExchange, chain)).verifyComplete();
        assertThat(oldExchange.getResponse().getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));

        ServerWebExchange newExchange = MockServerWebExchange.from(request);
        StepVerifier.create(filter.filter(newExchange, chain)).verifyComplete();
        assertThat(newExchange.getResponse().getStatusCode()).isNull();
        verify(userRepository).findByOpenId(TEST_OPEN_ID);
        verify(chain).filter(any(ServerWebExchange.class));
    }

    /**
     * 验证 {@code filter_withAuthorityInfrastructureFailure_shouldReturnServiceUnavailable} 场景。
     */
    @Test
    @DisplayName("认证依赖异常时应返回503而不是误报未授权")
    void filter_withAuthorityInfrastructureFailure_shouldReturnServiceUnavailable() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v2/explore/choice-2/current")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        Claims claims = mockClaims();
        when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
        when(userRepository.findByOpenId(TEST_OPEN_ID))
                .thenReturn(Mono.error(new IllegalStateException("db unavailable")));
        when(tokenVerifier.parse(VALID_TOKEN)).thenReturn(claims);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatusCode.valueOf(503));
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("过期Token应返回401错误")
    void filter_withExpiredToken_shouldReturnUnauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
        when(tokenVerifier.parse(VALID_TOKEN)).thenThrow(new ExpiredJwtException(null, null, "Token已过期"));

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
    }

    @Test
    @DisplayName("无效Token应返回401错误")
    void filter_withInvalidToken_shouldReturnUnauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
        when(tokenVerifier.parse(VALID_TOKEN)).thenThrow(new JwtException("无效的Token"));

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
    }

    @Test
    @DisplayName("添加Token到黑名单应委托给TokenBlacklistService")
    void addTokenToBlacklist_shouldDelegate() {
        long expireTime = 3600000L;

        when(tokenBlacklistService.add(VALID_TOKEN, expireTime)).thenReturn(Mono.empty());

        StepVerifier.create(filter.addTokenToBlacklist(VALID_TOKEN, expireTime)).verifyComplete();

        verify(tokenBlacklistService).add(VALID_TOKEN, expireTime);
    }

    @Nested
    @DisplayName("认证中心令牌分支（PC 管理端统一账户）")
    class CenterTokenTests {

        /** 构造认证中心访问令牌 claims（tokenType=ACCESS + accountId）。 */
        private Claims centerClaims(Long accountId, String tenantId) {
            Claims claims = org.mockito.Mockito.mock(Claims.class);
            when(claims.get("tokenType", String.class)).thenReturn("ACCESS");
            when(claims.get("accountId", Long.class)).thenReturn(accountId);
            when(claims.get("tenantId", String.class)).thenReturn(tenantId);
            when(claims.get("username", String.class)).thenReturn("admin");
            return claims;
        }

        /** 构造启用状态的统一账户实体。 */
        private com.apex.scaffold.domain.model.entity.UserEntity account(Long id, String tenantId) {
            com.apex.scaffold.domain.model.entity.UserEntity user =
                    com.apex.scaffold.domain.model.entity.UserEntity.create(TEST_OPEN_ID, "union-1");
            user.setId(id);
            user.setTenantId(tenantId);
            return user;
        }

        @Test
        @DisplayName("accountId 命中统一账户并复用主应用 RBAC 装配权限")
        void filter_withCenterToken_shouldAuthenticateViaAccountId() {
            Claims centerClaims = centerClaims(500L, "default");
            com.apex.scaffold.domain.model.entity.UserEntity account = account(500L, "default");
            RoleEntity adminRole = RoleEntity.create("管理员", "ADMIN", "系统管理员");

            when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
            when(tokenVerifier.parse(VALID_TOKEN)).thenReturn(centerClaims);
            when(userRepository.findById(500L)).thenReturn(Mono.just(account));
            when(roleRepository.findByUserId(500L)).thenReturn(Mono.just(java.util.List.of(adminRole)));
            when(chain.filter(any(ServerWebExchange.class)))
                    .thenAnswer(invocation ->
                            org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
                                    .switchIfEmpty(Mono.error(new AssertionError("Missing authentication")))
                                    .doOnNext(context -> assertThat(
                                                    context.getAuthentication().getAuthorities())
                                            .extracting(
                                                    org.springframework.security.core.GrantedAuthority::getAuthority)
                                            .containsExactly("ROLE_ADMIN"))
                                    .then(Mono.deferContextual(ctx -> {
                                        assertThat(ctx.<String>get(
                                                        com.apex.scaffold.common.context.TenantContext
                                                                .REACTOR_CONTEXT_KEY))
                                                .isEqualTo("default");
                                        return Mono.empty();
                                    })));

            ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/user/profile")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                    .build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            // MDC 用户标识与 C 端 userId 属性对齐（字符串形式）
            assertThat(exchange.getAttributes().get("userId")).isEqualTo("500");
            assertThat(exchange.getAttributes().get("tenantId")).isEqualTo("default");
            verify(userRepository).findById(500L);
            verify(roleRepository).findByUserId(500L);
        }

        @Test
        @DisplayName("账户被禁用时返回401且不进入过滤链")
        void filter_withCenterTokenDisabledAccount_shouldReturnUnauthorized() {
            Claims centerClaims = centerClaims(500L, "default");
            com.apex.scaffold.domain.model.entity.UserEntity account = account(500L, "default");
            account.setStatus(0);

            when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
            when(tokenVerifier.parse(VALID_TOKEN)).thenReturn(centerClaims);
            when(userRepository.findById(500L)).thenReturn(Mono.just(account));

            ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/user/profile")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                    .build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
            verifyNoInteractions(chain);
        }

        @Test
        @DisplayName("令牌租户与统一账户租户不一致时返回401")
        void filter_withCenterTokenTenantMismatch_shouldReturnUnauthorized() {
            Claims centerClaims = centerClaims(500L, "foreign-tenant");
            com.apex.scaffold.domain.model.entity.UserEntity account = account(500L, "default");

            when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
            when(tokenVerifier.parse(VALID_TOKEN)).thenReturn(centerClaims);
            when(userRepository.findById(500L)).thenReturn(Mono.just(account));

            ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/user/profile")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                    .build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
            verifyNoInteractions(chain);
        }

        @Test
        @DisplayName("accountId 对应的统一账户不存在时返回401")
        void filter_withCenterTokenMissingAccount_shouldReturnUnauthorized() {
            Claims centerClaims = centerClaims(999L, "default");

            when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
            when(tokenVerifier.parse(VALID_TOKEN)).thenReturn(centerClaims);
            when(userRepository.findById(999L)).thenReturn(Mono.empty());

            ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/user/profile")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                    .build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
            verifyNoInteractions(chain);
        }

        @Test
        @DisplayName("无 accountId 的 ACCESS 声明不进入认证中心分支（结构区分兜底）")
        void filter_withCenterTokenWithoutAccountId_shouldFallBackToUnauthorized() {
            Claims claimsWithoutAccount = org.mockito.Mockito.mock(Claims.class);
            when(claimsWithoutAccount.get("tokenType", String.class)).thenReturn("ACCESS");
            // accountId 未携带（如存量 auth_user 未建立映射时签发的旧令牌）

            when(tokenBlacklistService.isBlacklisted(VALID_TOKEN)).thenReturn(Mono.just(false));
            when(tokenVerifier.parse(VALID_TOKEN)).thenReturn(claimsWithoutAccount);

            ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/user/profile")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                    .build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
            verifyNoInteractions(userRepository, chain);
        }
    }

    /** 统一认证收口（A1）：真实 RS256 认证中心令牌经统一验签器走通主应用认证分支。 */
    @Test
    void centerRs256TokenAuthenticatesThroughRealVerifier() {
        java.security.KeyPair keyPair = io.jsonwebtoken.Jwts.SIG.RS256.keyPair().build();
        String kid = "key-it-0001";
        JwtVerifierSupplier supplier = new JwtVerifierSupplier(
                JwtVerifierSupplier.decodeConfiguredKeys(Map.of(
                        kid,
                        java.util.Base64.getEncoder()
                                .encodeToString(keyPair.getPublic().getEncoded()))),
                null,
                "system-filter-real-rs256-secret-0123456789abcdef",
                true);
        com.apex.scaffold.domain.gateway.UnifiedAuthorizationGateway gateway =
                org.mockito.Mockito.mock(com.apex.scaffold.domain.gateway.UnifiedAuthorizationGateway.class);
        com.apex.scaffold.application.service.UnifiedAuthorizationService unified =
                new com.apex.scaffold.application.service.UnifiedAuthorizationService(
                        gateway,
                        new org.springframework.mock.env.MockEnvironment()
                                .withProperty("apex.features.auth-center-permission", "true"),
                        null);
        JwtAuthenticationFilter real = new JwtAuthenticationFilter(
                tokenBlacklistService, null, messageUtils, userRepository, roleRepository, unified, supplier);
        UserEntity user = UserEntity.create(TEST_OPEN_ID, "reader");
        user.setId(95302L);
        user.setTenantId("default");
        when(userRepository.findById(95302L)).thenReturn(Mono.just(user));
        when(tokenBlacklistService.isBlacklisted(any(String.class))).thenReturn(Mono.just(false));
        when(gateway.query("default", 95302L))
                .thenReturn(Mono.just(new com.apex.scaffold.domain.gateway.UnifiedAuthorizationGateway.Authorization(
                        "default",
                        95302L,
                        java.util.List.of(),
                        java.util.List.of(new com.apex.scaffold.domain.gateway.UnifiedAuthorizationGateway.Role(
                                "ADMIN", "ALL")))));
        String token = io.jsonwebtoken.Jwts.builder()
                .header()
                .keyId(kid)
                .and()
                .subject("95302")
                .claim("tokenType", "ACCESS")
                .claim("accountId", 95302L)
                .claim("tenantId", "default")
                .claim("username", "reader")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 300_000))
                .signWith(keyPair.getPrivate(), io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();
        when(chain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation ->
                        org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
                                .switchIfEmpty(Mono.error(new AssertionError("Missing authentication")))
                                .doOnNext(context -> assertThat(
                                                context.getAuthentication().getAuthorities())
                                        .extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
                                        .containsExactly("ROLE_ADMIN"))
                                .then(Mono.empty()));
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build());
        StepVerifier.create(real.filter(exchange, chain)).verifyComplete();
        verify(gateway).query("default", 95302L);
    }

    private Claims mockClaims() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn(TEST_OPEN_ID);
        when(claims.get("type", String.class)).thenReturn("access");
        return claims;
    }
}
