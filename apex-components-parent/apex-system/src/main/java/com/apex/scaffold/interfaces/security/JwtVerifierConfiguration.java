package com.apex.scaffold.interfaces.security;

import com.apex.scaffold.common.security.JwksFetcher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 主应用统一令牌验签装配（统一认证收口 A1/A2）。
 *
 * <p>组合三类密钥来源：认证中心静态公钥兜底（{@code apex.system.jwt-center.public-keys}）、
 * 共享 JWKS 拉取快照（{@code apex.jwks.remote-url} 启用时，apex-common 自动装配提供）、
 * 主应用 HS256 过渡密钥（{@code jwt.secret}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
@EnableConfigurationProperties(CenterJwtKeyProperties.class)
public class JwtVerifierConfiguration {

    /**
     * 构建统一令牌验签器。
     *
     * @param properties  认证中心公钥配置
     * @param jwksFetcher 共享 JWKS 拉取客户端（可能未装配）
     * @param hmacSecret  主应用 HMAC 密钥
     * @return 统一令牌验签器
     */
    @Bean
    public JwtVerifierSupplier jwtVerifierSupplier(
            CenterJwtKeyProperties properties,
            ObjectProvider<JwksFetcher> jwksFetcher,
            @Value("${jwt.secret:}") String hmacSecret) {
        return new JwtVerifierSupplier(
                JwtVerifierSupplier.decodeConfiguredKeys(properties.getPublicKeys()),
                jwksFetcher.getIfAvailable(),
                hmacSecret,
                properties.isLegacyHmacEnabled());
    }
}
