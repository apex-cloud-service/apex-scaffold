package com.apex.scaffold.common.autoconfigure;

import com.apex.scaffold.common.openapi.OpenApiCapabilityGuardFilter;
import com.apex.scaffold.common.openapi.OpenApiGuardProperties;
import com.apex.scaffold.common.openapi.TenantCapabilityVerifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 装配服务侧开放接口能力二次校验组件（方案 D4 共用判定）。
 *
 * <p>默认装配：租户能力校验客户端 + 守卫过滤器。守卫仅对 {@code pathPrefix}
 * 命中的开放路径或携带可信标记头的请求做二次校验，普通流量直接透传、不产生远程调用。
 * 确需关闭的实例显式配置 {@code apex.open-api.guard.enabled=false}，此时零 Bean。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "apex.open-api.guard", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OpenApiGuardProperties.class)
public class OpenApiGuardAutoConfiguration {

    /**
     * 创建租户能力校验客户端。
     *
     * @param properties 守卫配置
     * @return 校验客户端
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantCapabilityVerifier tenantCapabilityVerifier(
            OpenApiGuardProperties properties,
            com.apex.scaffold.common.internal.InternalAuthProperties internalAuthProperties,
            com.apex.scaffold.common.internal.InternalServiceSigner internalServiceSigner) {
        return new TenantCapabilityVerifier(properties, internalAuthProperties, internalServiceSigner);
    }

    /**
     * 创建服务侧能力二次校验过滤器。
     *
     * @param properties 守卫配置
     * @param verifier 校验客户端
     * @return 守卫过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenApiCapabilityGuardFilter openApiCapabilityGuardFilter(
            OpenApiGuardProperties properties, TenantCapabilityVerifier verifier) {
        return new OpenApiCapabilityGuardFilter(properties, verifier);
    }
}
