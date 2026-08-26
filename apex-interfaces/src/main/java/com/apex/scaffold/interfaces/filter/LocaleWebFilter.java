package com.apex.scaffold.interfaces.filter;

import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 语言区域（Locale）解析过滤器
 *
 * <p>基于 Spring WebFlux 的 {@link WebFilter} 实现，在请求进入业务处理前
 * 解析客户端 {@code Accept-Language} 请求头，确定当前请求的语言偏好，
 * 并将解析出的 {@link Locale} 写入 Reactor Context 中供下游使用。
 *
 * <h2>为什么使用 WebFilter 而非 LocaleResolver</h2>
 * <p>
 * Spring MVC 中常用的 {@code LocaleResolver} 依赖 Servlet API，
 * 在 WebFlux 响应式架构中不可用。因此采用自定义 {@link WebFilter}
 * 配合 Reactor Context 实现语言信息的响应式传递。
 * </p>
 *
 * <h2>支持的语言列表</h2>
 * <table border="1">
 *   <caption>支持的语言列表</caption>
 *   <tr><th>语言</th><th>Locale</th><th>Accept-Language 值示例</th></tr>
 *   <tr><td>简体中文</td><td>zh_CN</td><td>zh-CN</td></tr>
 *   <tr><td>繁体中文</td><td>zh_TW</td><td>zh-TW</td></tr>
 *   <tr><td>英文</td><td>en_US</td><td>en-US</td></tr>
 *   <tr><td>泰文</td><td>th_TH</td><td>th-TH</td></tr>
 * </table>
 *
 * <h2>语言解析策略</h2>
 * <ol>
 *   <li>从请求头中获取 {@code Accept-Language} 值</li>
 *   <li>使用 {@link Locale.LanguageRange#parse(String)} 解析语言优先级</li>
 *   <li>通过 {@link Locale#lookup(List, java.util.Collection)} 匹配最佳支持语言</li>
 *   <li>若客户端未提供或语言不支持，默认使用简体中文（zh_CN）</li>
 * </ol>
 *
 * <h2>过滤器执行顺序</h2>
 * <p>
 * 设置 {@code @Order(Ordered.HIGHEST_PRECEDENCE + 10)}，
 * 确保在 JWT 认证过滤器和其他业务过滤器之前执行，
 * 使 Locale 信息在整个请求链中都可用。
 * </p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.interfaces.util.MessageUtils
 * @see java.util.Locale
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LocaleWebFilter implements WebFilter {

    /**
     * 当前系统支持的语言列表
     *
     * <p>按优先级排列，{@link Locale#lookup} 会根据客户端
     * Accept-Language 权重值从此列表中选择最佳匹配。
     */
    private static final List<Locale> SUPPORTED_LOCALES =
            List.of(Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE, Locale.US, Locale.forLanguageTag("th-TH"));

    /**
     * 默认语言：简体中文
     *
     * <p>当客户端未提供 Accept-Language 头或提供的语言均不支持时使用。
     */
    private static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;

    /**
     * 执行语言解析过滤
     *
     * <p>从请求中解析出 Locale 后，通过 Reactor 的
     * {@code contextWrite} 机制将其写入上下文，
     * 确保整个响应式链路都能访问到。
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @param chain    WebFlux 过滤器链
     * @return 表示过滤完成的 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Locale locale = resolveLocale(exchange);
        return chain.filter(exchange).contextWrite(ctx -> ctx.put(Locale.class, locale));
    }

    /**
     * 从请求头解析语言偏好
     *
     * <p>解析 {@code Accept-Language} 请求头，使用 RFC 4647 的
     * {@link Locale.LanguageRange} 机制匹配最佳语言。若客户端的
     * Accept-Language 格式无效，则安全降级为默认语言。
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @return 解析出的 Locale，若无法匹配则返回默认值 zh_CN
     */
    private Locale resolveLocale(ServerWebExchange exchange) {
        String acceptLanguage = exchange.getRequest().getHeaders().getFirst("Accept-Language");
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return DEFAULT_LOCALE;
        }

        List<Locale.LanguageRange> languageRanges;
        try {
            languageRanges = Locale.LanguageRange.parse(acceptLanguage);
        } catch (IllegalArgumentException e) {
            log.debug("Accept-Language解析失败，使用默认语言: {}", acceptLanguage, e);
            return DEFAULT_LOCALE;
        }

        Locale bestMatch = Locale.lookup(languageRanges, SUPPORTED_LOCALES);
        return bestMatch != null ? bestMatch : DEFAULT_LOCALE;
    }
}
