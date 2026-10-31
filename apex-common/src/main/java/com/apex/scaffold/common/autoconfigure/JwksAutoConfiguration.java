package com.apex.scaffold.common.autoconfigure;

import com.apex.scaffold.common.config.JwksFetchProperties;
import com.apex.scaffold.common.security.JwksFetcher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 装配共享 JWKS 拉取客户端（统一认证收口 A2）。
 *
 * <p>网关与各业务服务通过自动装配获得 {@link JwksFetcher}；{@code apex.jwks.remote-url}
 * 为空时组件处于禁用态（不发起任何网络请求），验签方回退到静态公钥兜底。
 * 仅在响应式 Web 应用中装配，避免非 Web 场景引入无用 Bean。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@EnableConfigurationProperties(JwksFetchProperties.class)
public class JwksAutoConfiguration {
    /**
     * 创建共享 JWKS 拉取客户端。
     *
     * @param properties JWKS 拉取配置
     * @return JWKS 拉取客户端
     */
    @Bean
    @ConditionalOnMissingBean
    public JwksFetcher jwksFetcher(JwksFetchProperties properties) {
        return new JwksFetcher(properties);
    }
}
