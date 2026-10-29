package com.apex.scaffold.interfaces.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * 国际化（i18n）配置类
 *
 * <p>配置 Spring 多语言支持的 MessageSource 和 JSR-380 Bean Validation 的国际化消息源。
 * 使用 {@link ReloadableResourceBundleMessageSource} 支持运行时热加载语言资源文件，
 * 语言资源文件存放在 {@code classpath:i18n/} 目录下。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>构建 {@link MessageSource} Bean，加载 {@code classpath:i18n/messages} 下的多语言资源文件</li>
 *   <li>设置 UTF-8 编码、关闭系统区域回退、未找到 key 时返回 key 本身、缓存 3600 秒</li>
 *   <li>将 MessageSource 注入 {@link LocalValidatorFactoryBean}，使 DTO 校验注解中的 {@code {key}} 占位符可被解析</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>简体中文（zh_CN）</li>
 *   <li>繁体中文（zh_TW）</li>
 *   <li>英文（en_US）</li>
 *   <li>泰文（th_TH）</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><b>basename</b>：资源文件基础路径，不包括语言后缀和 .properties 扩展名</li>
 *   <li><b>encoding</b>：资源文件编码，设置为 UTF-8 确保多语言字符正常显示</li>
 *   <li><b>fallbackToSystemLocale</b>：关闭系统区域回退，避免忽略客户端语言偏好</li>
 *   <li><b>useCodeAsDefaultMessage</b>：未找到对应 key 时返回 key 本身作为消息</li>
 *   <li><b>cacheSeconds</b>：缓存时间（秒），生产环境建议设置合理值避免频繁读取文件</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>
 * 通过 {@link LocalValidatorFactoryBean#setValidationMessageSource(MessageSource)}
 * 将 MessageSource 注入 Jakarta Bean Validation，使 DTO 校验注解中的 {@code {key}}
 * 占位符能被正确解析为对应语言的校验消息。
 * </p>
 *
 * <h2>设计说明</h2>
 * <ol>
 *   <li>创建对应的 {@code messages_{locale}.properties} 文件（如 {@code messages_ja_JP.properties}）</li>
 *   <li>在 {@link com.apex.scaffold.interfaces.filter.LocaleWebFilter} 的 {@code SUPPORTED_LOCALES} 中添加对应的 Locale</li>
 *   <li>无需修改此类任何代码</li>
 * </ol>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see com.apex.scaffold.interfaces.filter.LocaleWebFilter
 * @see com.apex.scaffold.interfaces.util.MessageUtils
 */
@Configuration
public class I18nConfig {

    /**
     * 创建 MessageSource Bean，用于解析国际化消息
     *
     * <p>基于 {@link ReloadableResourceBundleMessageSource} 实现，
     * 支持从 classpath 下的 i18n 目录加载多语言资源文件并缓存。
     * 当找不到对应语言的消息时，会使用 key 本身作为默认消息返回。
     *
     * @return 配置完成的 MessageSource 实例
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

    /**
     * 创建本地化校验器 Bean，将 MessageSource 注入 JSR-380 Bean Validation
     *
     * <p>
     * 通过 {@link LocalValidatorFactoryBean#setValidationMessageSource(MessageSource)}
     * 使得 DTO 注解中的 {@code {param.xxx}} 格式的占位符能够从多语言资源文件中解析。
     * 这样 {@code @NotBlank(message = "{param.not_null}")} 就能根据客户端语言
     * 自动输出对应语言的校验消息。
     * </p>
     *
     * @param messageSource 国际化消息源
     * @return 配置完成的 LocalValidatorFactoryBean 实例
     */
    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        factoryBean.setValidationMessageSource(messageSource);
        return factoryBean;
    }
}
