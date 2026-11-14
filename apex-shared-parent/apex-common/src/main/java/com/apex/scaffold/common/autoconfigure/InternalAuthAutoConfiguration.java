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
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@link InternalAuthProperties} 为纯配置属性，始终注册：出站客户端适配器
 *       （如租户状态回源、能力校验、计量回源）把它作为构造依赖，若随
 *       {@code enabled=false} 一起消失会导致上下文启动失败。</li>
 *   <li>仅签名器与入站校验过滤器受 {@code apex.internal-auth.enabled} 控制：
 *       关闭时入站不再校验签名，出站适配器按“未配置签名”降级。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@AutoConfiguration
@EnableConfigurationProperties(InternalAuthProperties.class)
public class InternalAuthAutoConfiguration {

    /**
     * 签名器。
     *
     * <p>始终注册：出站适配器把它作为构造依赖；未配置密钥时
     * {@code configured()} 为 false，出站自动跳过签名、入站过滤器不装配，
     * 与 {@code apex.internal-auth.enabled=false} 语义一致。
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
     * 内部请求校验过滤器（仅在内部认证启用且当前为响应式 Web 应用时装配）。
     *
     * @param properties 配置
     * @param signer 签名器
     * @return 过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnProperty(prefix = "apex.internal-auth", name = "enabled", havingValue = "true", matchIfMissing = true)
    public InternalAuthWebFilter internalAuthWebFilter(
            InternalAuthProperties properties, InternalServiceSigner signer) {
        return new InternalAuthWebFilter(properties, signer, false);
    }
}
