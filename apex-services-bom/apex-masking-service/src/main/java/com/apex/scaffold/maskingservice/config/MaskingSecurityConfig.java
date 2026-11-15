package com.apex.scaffold.maskingservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * 独立验证 JWT，不信任身份透传头。
 *
 * <p>兼容两类令牌家族：认证中心 OAuth2 令牌（携带 {@code token_use}，按签发者/受众校验，
 * scope 由处理器按家族校验）与统一认证登录令牌（携带 {@code tokenType=ACCESS}，按统一认证
 * 契约校验并由业务层租户白名单约束）。两类令牌均由共享 JWKS 完成签名与有效期校验。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
public class MaskingSecurityConfig {
    /** 登录令牌家族声明：统一认证签发的访问令牌固定携带。 */
    public static final String LOGIN_TOKEN_TYPE_CLAIM = "tokenType";
    /** 登录访问令牌的固定取值。 */
    public static final String LOGIN_ACCESS_TYPE = "ACCESS";
    /** OAuth2 令牌家族声明：OAuth2 授权端点签发的令牌固定携带。 */
    public static final String OAUTH2_TOKEN_USE_CLAIM = "token_use";

    /**
     * 创建验签器并按令牌家族校验：OAuth2 令牌约束签发者、受众与有效期；登录令牌校验 ACCESS 声明与有效期。
     * @param properties 配置
     * @return JWT 验签器
     */
    @Bean
    public ReactiveJwtDecoder maskingJwtDecoder(MaskingProperties properties) {
        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                new JwtTimestampValidator(), familyAwareValidator(properties.issuer(), properties.audience())));
        return decoder;
    }

    /**
     * 注册无状态安全链：端点仅要求已认证，OAuth2 令牌的 scope 要求由处理器按家族校验。
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
                        .anyExchange()
                        .authenticated())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> {}))
                .build();
    }

    /**
     * 按令牌家族选择校验规则：OAuth2 令牌校验签发者与受众；登录令牌校验 ACCESS 声明；两者皆无则拒绝。
     * @param issuer OAuth2 令牌的可信签发者
     * @param audience OAuth2 令牌的受众
     * @return 家族感知校验器
     */
    private static OAuth2TokenValidator<Jwt> familyAwareValidator(String issuer, String audience) {
        return token -> {
            if (token.getClaims().get(OAUTH2_TOKEN_USE_CLAIM) != null) {
                return new DelegatingOAuth2TokenValidator<Jwt>(
                                new JwtTimestampValidator(),
                                new JwtIssuerValidator(issuer),
                                audienceValidator(audience))
                        .validate(token);
            }
            return LOGIN_ACCESS_TYPE.equals(token.getClaimAsString(LOGIN_TOKEN_TYPE_CLAIM))
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "令牌缺少可识别的签发家族声明", null));
        };
    }

    /**
     * 校验受众声明包含配置的服务受众。
     * @param audience 配置的服务受众
     * @return 受众校验器
     */
    private static JwtClaimValidator<java.util.List<String>> audienceValidator(String audience) {
        return new JwtClaimValidator<>("aud", values -> values != null && values.contains(audience));
    }
}
