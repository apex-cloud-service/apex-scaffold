package com.apex.scaffold.authservice.config;

import static org.mockito.Mockito.mock;

import com.apex.scaffold.auth.application.service.RoleService;
import com.apex.scaffold.auth.domain.service.TokenService;
import com.apex.scaffold.common.config.JwtProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 认证中心安全链公开路径测试。
 *
 * <p>MFA 二步交换在签发访问令牌之前调用，必须匿名可达；其余 MFA 管理端点仍要求
 * ACCESS 令牌。本测试直接装配真实 {@link AuthCenterConfig} 安全链，避免只断言常量而
 * 未覆盖过滤器实际放行行为。
 *
 * @author Apex Team
 * @since 1.3.1
 */
class AuthCenterPublicPathSecurityTest {
    private AnnotationConfigApplicationContext context;
    private WebTestClient client;
    private TokenService tokens;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        tokens = context.getBean(TokenService.class);
        client = WebTestClient.bindToApplicationContext(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName("MFA 二步交换无需访问令牌即可到达业务路由")
    void mfaChallengeExchangeIsAnonymous() {
        client.post().uri("/auth/v1/mfa/verify").exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("自助 MFA 管理端点仍要求访问令牌")
    void mfaManagementRequiresToken() {
        client.post().uri("/api/v1/mfa/setup").exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("密码找回入口匿名可达")
    void passwordRecoveryIsAnonymous() {
        client.post()
                .uri("/auth/v2/password-recovery/requests")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    @DisplayName("JWKS 公钥端点匿名可达")
    void jwksIsAnonymous() {
        client.get().uri("/.well-known/jwks.json").exchange().expectStatus().isOk();
    }

    @Configuration
    @EnableWebFlux
    @EnableWebFluxSecurity
    static class TestConfiguration {
        @Bean
        TokenService tokens() {
            return mock(TokenService.class);
        }

        @Bean
        RoleService roles() {
            return mock(RoleService.class);
        }

        @Bean
        SecurityWebFilterChain security(ServerHttpSecurity http, TokenService tokens, RoleService roles) {
            return new AuthCenterConfig()
                    .securityWebFilterChain(http, tokens, roles, new JwtProperties(), new MockEnvironment());
        }

        @Bean
        RouterFunction<ServerResponse> routes() {
            return RouterFunctions.route()
                    .POST("/auth/v1/mfa/verify", request -> ServerResponse.ok().build())
                    .POST("/api/v1/mfa/setup", request -> ServerResponse.ok().build())
                    .POST("/auth/v2/password-recovery/requests", request -> ServerResponse.ok()
                            .build())
                    .GET("/.well-known/jwks.json", request -> ServerResponse.ok()
                            .build())
                    .build();
        }
    }
}
