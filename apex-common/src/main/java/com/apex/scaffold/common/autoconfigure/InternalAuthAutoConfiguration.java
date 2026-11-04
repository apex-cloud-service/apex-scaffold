package com.apex.scaffold.common.autoconfigure;

import com.apex.scaffold.common.internal.InternalAuthProperties;
import com.apex.scaffold.common.internal.InternalAuthWebFilter;
import com.apex.scaffold.common.internal.InternalServiceSigner;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 内部接口签名校验自动装配（{@code apex.internal-auth.*}）。
 *
 * <p>默认开启；关闭仅用于本地开发。启用后所有服务的 {@code /internal/v1/**} 都要求
 * HMAC 签名，不再依赖 {@code permitAll} 或网络边界。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "apex.internal-auth", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(InternalAuthProperties.class)
public class InternalAuthAutoConfiguration {

    /**
     * 签名器。
     *
     * @param properties 配置
     * @return 签名器
     */
    @Bean
    @ConditionalOnMissingBean
    public InternalServiceSigner internalServiceSigner(InternalAuthProperties properties) {
        return InternalAuthWebFilter.signer(properties, Clock.systemUTC());
    }

    /**
     * 内部请求校验过滤器。
     *
     * @param properties 配置
     * @param signer 签名器
     * @return 过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    public InternalAuthWebFilter internalAuthWebFilter(
            InternalAuthProperties properties, InternalServiceSigner signer) {
        return new InternalAuthWebFilter(properties, signer, false);
    }
}
