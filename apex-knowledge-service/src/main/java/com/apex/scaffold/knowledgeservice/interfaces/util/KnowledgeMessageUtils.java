package com.apex.scaffold.knowledgeservice.interfaces.util;

import com.apex.scaffold.knowledgeservice.config.KnowledgeI18nConfig;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 响应式国际化消息解析工具类。
 *
 * <p>经 {@code Mono.deferContextual} 从 Reactor Context 读取当前请求 Locale
 * （由 {@code KnowledgeLocaleWebFilter} 写入），从 {@link MessageSource} 解析消息；
 * 非响应式上下文使用默认简体中文。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Component
public class KnowledgeMessageUtils {

    private final MessageSource messageSource;

    /**
     * 注入消息源。
     *
     * @param messageSource 国际化消息源
     */
    public KnowledgeMessageUtils(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * 响应式解析当前请求语言的消息。
     *
     * @param key 消息键
     * @return 消息文本
     */
    public Mono<String> get(String key) {
        return Mono.deferContextual(
                context -> Mono.just(get(context.getOrDefault(Locale.class, KnowledgeI18nConfig.DEFAULT_LOCALE), key)));
    }

    /**
     * 按指定语言解析消息（非响应式上下文）。
     *
     * @param locale 语言
     * @param key 消息键
     * @return 消息文本
     */
    public String get(Locale locale, String key) {
        return messageSource.getMessage(key, null, locale);
    }
}
