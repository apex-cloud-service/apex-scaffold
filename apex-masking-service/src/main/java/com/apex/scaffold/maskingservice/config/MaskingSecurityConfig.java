package com.apex.scaffold.maskingservice.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * 独立验证 JWT，不信任身份透传头；读规则与执行规则分别授权。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
public class MaskingSecurityConfig {
    /**
     * 创建验签器并约束签发者、时间和受众。
     * @param properties 配置
     * @return JWT 验签器
     */
    @Bean
    public ReactiveJwtDecoder maskingJwtDecoder(MaskingProperties properties) {
        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        var audience = new JwtClaimValidator<List<String>>(
                "aud", values -> values != null && values.contains(properties.audience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), audience));
        return decoder;
    }

    /**
     * 注册无状态安全链。
     * @param http 安全构建器
     * @return 安全链
     */
    @Bean
    public SecurityWebFilterChain maskingSecurityChain(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(auth -> auth.pathMatchers("/actuator/health")
                        .permitAll()
                        .pathMatchers("/api/v1/masking/batch")
                        .hasAuthority("SCOPE_masking:apply")
                        .pathMatchers("/api/v1/masking/rules")
                        .hasAuthority("SCOPE_masking:rules")
                        .anyExchange()
                        .authenticated())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> {}))
                .build();
    }
}
