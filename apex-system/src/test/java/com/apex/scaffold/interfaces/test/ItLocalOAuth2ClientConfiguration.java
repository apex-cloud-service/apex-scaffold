package com.apex.scaffold.interfaces.test;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * 本地外部依赖 IT（it-local）的 OAuth2 客户端注册替身。
 *
 * <p>生产配置在 {@code spring.security.oauth2.client.provider.keycloak.issuer-uri} 声明了
 * Keycloak OIDC Discovery 地址，Spring Security 会在上下文启动期拉取
 * {@code /.well-known/openid-configuration}。{@code it-local} 用于验证真实 MySQL/Redis 语义，
 * 不启动 Keycloak，因此以内存注册表覆盖该发现流程，避免外部依赖阻断上下文加载。
 *
 * <p>注册项为静态占位（不发起任何网络调用），仅满足
 * {@code ReactiveOAuth2AuthorizedClientManager} 的依赖；真实 OIDC 流程由
 * {@code keycloak.enabled=true} 的环境验证。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration(proxyBeanMethods = false)
@Profile("it-local")
public class ItLocalOAuth2ClientConfiguration {

    /**
     * 内存版 OAuth2 客户端注册表（无 OIDC Discovery 网络调用）。
     *
     * @return 静态注册表
     */
    @Bean
    public ReactiveClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration placeholder = ClientRegistration.withRegistrationId("keycloak")
                .clientId("it-local-placeholder")
                .clientSecret("it-local-placeholder")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid")
                .authorizationUri("http://localhost:8180/realms/apex/protocol/openid-connect/auth")
                .tokenUri("http://localhost:8180/realms/apex/protocol/openid-connect/token")
                .userInfoUri("http://localhost:8180/realms/apex/protocol/openid-connect/userinfo")
                .userNameAttributeName("preferred_username")
                .clientName("Keycloak (it-local placeholder)")
                .build();
        return new InMemoryReactiveClientRegistrationRepository(placeholder);
    }
}
