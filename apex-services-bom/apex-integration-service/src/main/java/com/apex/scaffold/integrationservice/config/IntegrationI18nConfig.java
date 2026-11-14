package com.apex.scaffold.integrationservice.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * 集成中心国际化配置（方案 M5 / 交付物补齐）。
 *
 * <p>加载 {@code classpath:i18n/messages} 下的中/繁中/英/泰四语言资源。
 * 未命中 key 时返回 key 本身（便于发现缺失翻译），UTF-8 编码，缓存 3600 秒。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration(proxyBeanMethods = false)
public class IntegrationI18nConfig {

    /**
     * 集成中心消息源。
     *
     * @return MessageSource
     */
    @Bean
    public MessageSource integrationMessageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(true);
        source.setCacheSeconds(3600);
        return source;
    }
}
