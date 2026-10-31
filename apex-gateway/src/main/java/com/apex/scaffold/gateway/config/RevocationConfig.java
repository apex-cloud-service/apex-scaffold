package com.apex.scaffold.gateway.config;

import com.apex.scaffold.gateway.security.RevocationCacheService;
import com.apex.scaffold.gateway.security.RevocationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关令牌撤销流装配（统一认证收口 A3）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
@EnableConfigurationProperties(RevocationProperties.class)
public class RevocationConfig {

    /**
     * 创建撤销缓存服务（{@code apex.gateway.revocation.enabled=false} 时处于禁用态，不发起拉取）。
     *
     * @param properties 撤销流配置
     * @return 撤销缓存服务
     */
    @Bean
    @ConditionalOnMissingBean
    public RevocationCacheService revocationCacheService(RevocationProperties properties) {
        return new RevocationCacheService(properties);
    }
}
