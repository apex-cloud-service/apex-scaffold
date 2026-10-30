package com.apex.scaffold.authservice.config;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * auth-service 国际化配置。
 *
 * <p>auth-service 的组件扫描范围仅覆盖 {@code com.apex.scaffold.authservice}、
 * {@code com.apex.scaffold.auth} 与 {@code com.apex.scaffold.common}，不含 system 的
 * {@code interfaces.config.I18nConfig}，因此需要在本服务内单独装配 {@link MessageSource}。
 * 资源位于 {@code classpath:i18n/messages_*.properties}，与 system 保持同一约定。</p>
 *
 * <p>找不到对应 key 时以 key 本身作为消息返回（{@code useCodeAsDefaultMessage}），
 * 避免因资源缺失把用户可见文案变成空串。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Configuration
public class AuthI18nConfig {

    /** 默认语言：简体中文，用于无法解析请求 Locale 的异步链路。 */
    public static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;

    /**
     * 创建消息源。
     *
     * @return 加载 {@code classpath:i18n/messages} 的消息源
     */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(true);
        messageSource.setCacheSeconds(3600);
        return messageSource;
    }
}
