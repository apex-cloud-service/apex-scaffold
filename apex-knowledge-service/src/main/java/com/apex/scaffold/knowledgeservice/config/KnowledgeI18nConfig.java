package com.apex.scaffold.knowledgeservice.config;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * 知识库服务国际化配置。
 *
 * <p>消息源加载 {@code classpath:i18n/messages}，未命中 key 时以 code 作为默认值；
 * 默认语言为简体中文（与认证中心配置保持一致）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class KnowledgeI18nConfig {

    /** 默认语言：简体中文，用于无法解析请求 Locale 的异步链路。 */
    public static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;

    /**
     * 创建消息源。
     *
     * @return 加载 {@code classpath:i18n/messages} 的消息源
     */
    @Bean
    public MessageSource knowledgeMessageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(true);
        messageSource.setCacheSeconds(3600);
        return messageSource;
    }
}
