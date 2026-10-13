package com.apex.scaffold.officeservice.interfaces.util;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 响应式国际化消息解析工具类
 *
 * <p>在 Spring WebFlux 响应式架构中，提供从 Reactor Context 中获取当前请求的
 * {@link Locale} 并从 {@link MessageSource} 中解析对应语言消息的能力。
 *
 * <h2>设计原理</h2>
 * <p>
 * 传统 Spring MVC 使用 {@code LocaleResolver} 和 {@code LocaleContextHolder}
 * 通过 ThreadLocal 传递 Locale。在 WebFlux 响应式架构中，请求可能在多个线程间切换，
 * ThreadLocal 不再可靠。因此采用 Reactor Context 机制，由
 * {@link com.apex.scaffold.officeservice.interfaces.filter.LocaleWebFilter} 将解析出的 Locale 写入 Context，
 * 本工具类通过 {@link Mono#deferContextual} 从中读取。
 * </p>
 *
 * <h2>使用方式</h2>
 *
 * <h3>方式一：响应式调用（推荐）</h3>
 * <pre>{@code * return messageUtils.get("biz.user.not_found")
 *     .map(msg -> Result.error(4001, msg, traceId))
 *     .flatMap(result -> ServerResponse.ok().bodyValue(result));
 * }</pre>
 *
 * <h3>方式二：同步调用（用于非响应式上下文）</h3>
 * <pre>{@code * String msg = messageUtils.get(Locale.US, "sys.success");
 * }</pre>
 *
 * <h2>支持的语言</h2>
 * <ul>
 *   <li>简体中文（zh_CN）</li>
 *   <li>繁体中文（zh_TW）</li>
 *   <li>英文（en_US）</li>
 *   <li>泰文（th_TH）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.officeservice.interfaces.filter.LocaleWebFilter
 * @see com.apex.scaffold.officeservice.interfaces.config.I18nConfig
 */
@Component
public class MessageUtils {

    /** Spring 国际化消息源 */
    private final MessageSource messageSource;

    /**
     * 默认语言：简体中文
     *
     * <p>当 Reactor Context 中不存在 Locale 时（如非 HTTP 请求触发的任务）使用此默认值。
     */
    private static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;

    /**
     * 构造方法，注入 MessageSource
     *
     * @param messageSource Spring MessageSource，由 {@link com.apex.scaffold.officeservice.interfaces.config.I18nConfig} 创建
     */
    public MessageUtils(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * 从 Reactor Context 中获取 Locale 并解析对应语言的消息（无参数）
     *
     * <p>
     * 通过 {@link Mono#deferContextual} 从当前响应式链路的 Reactor Context 中
     * 读取由 {@link com.apex.scaffold.officeservice.interfaces.filter.LocaleWebFilter} 写入的 Locale，
     * 然后调用 {@link MessageSource#getMessage(String, Object[], String, Locale)} 获取消息。
     * 如果 Context 中不存在 Locale，则使用简体中文作为默认值。
     * </p>
     *
     * @param code 消息的 key，对应 properties 文件中的键
     * @return 包含对应语言消息文本的 Mono；若 key 不存在则返回 key 本身
     */
    public Mono<String> get(String code) {
        return Mono.deferContextual(ctx -> {
            Locale locale = ctx.getOrDefault(Locale.class, DEFAULT_LOCALE);
            return Mono.just(messageSource.getMessage(code, null, code, locale));
        });
    }

    /**
     * 从 Reactor Context 中获取 Locale 并解析对应语言的消息（带参数）
     *
     * <p>
     * 适用于包含占位符的消息模板，如 {@code "文件大小超过限制，最大允许 {0}MB"}。
     * 使用 {@link java.text.MessageFormat} 格式化参数。
     * </p>
     *
     * @param code 消息的 key，对应 properties 文件中的键
     * @param args 消息模板中的参数，按顺序替换 {0}、{1} 等占位符
     * @return 包含格式化后消息文本的 Mono；若 key 不存在则返回 key 本身
     */
    public Mono<String> get(String code, Object... args) {
        return Mono.deferContextual(ctx -> {
            Locale locale = ctx.getOrDefault(Locale.class, DEFAULT_LOCALE);
            return Mono.just(messageSource.getMessage(code, args, code, locale));
        });
    }

    /**
     * 使用指定 Locale 同步解析消息（无参数）
     *
     * <p>
     * 适用于非响应式上下文（如过滤器、Security 配置等无法使用 Reactor Context 的场景）。
     * 调用方需要自行获取 Locale（通常从 {@link com.apex.scaffold.officeservice.interfaces.filter.LocaleWebFilter}
     * 写入的 Context 中读取）。
     * </p>
     *
     * @param locale 目标语言的 Locale 对象
     * @param code   消息的 key，对应 properties 文件中的键
     * @return 对应语言的消息文本；若 key 不存在则返回 key 本身
     */
    public String get(Locale locale, String code) {
        return messageSource.getMessage(code, null, code, locale);
    }

    /**
     * 使用指定 Locale 同步解析消息（带参数）
     *
     * <p>
     * 适用于非响应式上下文中需要参数格式化的场景。
     * </p>
     *
     * @param locale 目标语言的 Locale 对象
     * @param code   消息的 key，对应 properties 文件中的键
     * @param args   消息模板中的参数，按顺序替换 {0}、{1} 等占位符
     * @return 格式化后的对应语言消息文本；若 key 不存在则返回 key 本身
     */
    public String get(Locale locale, String code, Object... args) {
        return messageSource.getMessage(code, args, code, locale);
    }
}
