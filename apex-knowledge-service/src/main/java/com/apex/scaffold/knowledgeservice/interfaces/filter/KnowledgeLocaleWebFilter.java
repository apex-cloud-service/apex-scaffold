package com.apex.scaffold.knowledgeservice.interfaces.filter;

import com.apex.scaffold.knowledgeservice.config.KnowledgeI18nConfig;
import java.util.List;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 请求语言解析过滤器：将解析出的 Locale 写入 Reactor Context。
 *
 * <p>支持 zh_CN / zh_TW / en_US / th_TH；客户端未提供 Accept-Language 或语言
 * 均不支持时回退简体中文。下游经 {@code Mono.deferContextual} 读取。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class KnowledgeLocaleWebFilter implements WebFilter {

    /** 当前系统支持的语言列表（按优先级排列）。 */
    private static final List<Locale> SUPPORTED_LOCALES =
            List.of(Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE, Locale.US, Locale.forLanguageTag("th-TH"));

    /**
     * 执行语言解析过滤。
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @param chain WebFlux 过滤器链
     * @return 携带 Locale 上下文的完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Locale locale = resolveLocale(exchange);
        return chain.filter(exchange).contextWrite(reactor.util.context.Context.of(Locale.class, locale));
    }

    /**
     * 按 Accept-Language 解析最佳匹配语言。
     *
     * @param exchange 请求
     * @return 解析出的 Locale（未命中回退默认值）
     */
    private Locale resolveLocale(ServerWebExchange exchange) {
        String acceptLanguage = exchange.getRequest().getHeaders().getFirst("Accept-Language");
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return KnowledgeI18nConfig.DEFAULT_LOCALE;
        }
        List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(acceptLanguage);
        Locale matched = Locale.lookup(ranges, SUPPORTED_LOCALES);
        return matched == null ? KnowledgeI18nConfig.DEFAULT_LOCALE : matched;
    }
}
