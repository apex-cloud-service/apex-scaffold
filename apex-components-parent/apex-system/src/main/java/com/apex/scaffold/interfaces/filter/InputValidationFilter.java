package com.apex.scaffold.interfaces.filter;

import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.LogSafetyUtil;
import com.apex.scaffold.interfaces.monitor.SecurityMetricsRecorder;
import com.apex.scaffold.interfaces.util.MessageUtils;
import com.apex.scaffold.interfaces.util.ResponseUtil;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 输入验证过滤器
 *
 * <p>对 HTTP 请求的输入数据进行安全验证，检测并拦截包含恶意内容的请求，
 * 包括路径遍历攻击、SQL 注入和跨站脚本攻击（XSS）。作为全局安全防护的第一道防线，
 * 在请求进入业务处理之前完成输入校验。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li><b>路径遍历检测</b>：通过 {@link URI#getRawPath()} 检测请求路径中的
 *       {@code ../} 路径遍历特征，防止目录穿越攻击</li>
 *   <li><b>XSS 攻击检测</b>：通过正则表达式匹配查询参数和请求体中的 XSS 攻击特征，
 *       包括 script 标签、javascript: 协议、onEvent 事件处理器、iframe 嵌入、
 *       data:text/html 协议等</li>
 *   <li><b>SQL 注入检测</b>：通过正则表达式匹配 SQL 关键字、注释符和特殊字符组合，
 *       防止 SQL 注入攻击</li>
 *   <li><b>参数长度验证</b>：检测请求参数长度是否超过配置的最大限制</li>
 *   <li><b>Content-Type 验证</b>：验证请求的 Content-Type 是否在允许的白名单中</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <table border="1">
 *   <caption>XSS 攻击特征正则模式</caption>
 *   <tr><th>模式</th><th>正则表达式</th><th>说明</th></tr>
 *   <tr><td>script 标签</td><td>{@code <script[^>]*>.*?</script>}</td><td>匹配 JavaScript 脚本注入</td></tr>
 *   <tr><td>javascript: 协议</td><td>{@code javascript:}</td><td>匹配伪协议注入</td></tr>
 *   <tr><td>事件处理器</td><td>{@code on\w+\s*=}</td><td>匹配 HTML 事件属性注入</td></tr>
 *   <tr><td>iframe 标签</td><td>{@code <iframe[^>]*>}</td><td>匹配页面框架注入</td></tr>
 *   <tr><td>data: 协议（HTML）</td><td>{@code data:text/html}</td><td>匹配 HTML 数据注入</td></tr>
 * </table>
 *
 * <h2>使用示例</h2>
 * <p>通过 {@link InputValidationProperties} 配置各项验证的开关与参数，
 * 在 {@code application.yml} 中以 {@code apex.security.input-validation} 为前缀进行配置。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see InputValidationProperties
 * @see ResponseUtil
 */
@Slf4j
@Component
@Order(-180)
@RequiredArgsConstructor
public class InputValidationFilter implements WebFilter {

    /**
     * 返回国际化的输入验证失败JSON错误响应。
     *
     * <p>从 Reactor Context 中获取客户端语言偏好，通过 {@link MessageUtils}
     * 解析对应语言的消息文本，写入HTTP 400错误响应。
     *
     * @param exchange   当前 HTTP 请求-响应交换对象
     * @param messageKey 国际化消息键，对应 messages_*.properties 中的 key
     */
    private Mono<Void> buildI18nError(ServerWebExchange exchange, String messageKey) {
        return Mono.deferContextual(ctx -> {
            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
            String msg = messageUtils.get(locale, messageKey);
            return ResponseUtil.error(exchange, HttpStatus.BAD_REQUEST, ResultCode.PARAM_ERROR.getCode(), msg);
        });
    }

    /** XSS 攻击特征正则表达式模式列表 */
    private static final Pattern[] XSS_PATTERNS = {
        Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<iframe[^>]*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("data:text/html", Pattern.CASE_INSENSITIVE)
    };

    /** SQL 注入攻击特征正则表达式模式列表 */
    private static final Pattern[] SQL_INJECTION_PATTERNS = {
        // 检测 SELECT/INSERT/UPDATE/DELETE/DROP/UNION 等 SQL 关键字。
        // 要求关键字处于 SQL 语法位置（前为空白/引号/括号/逗号/分号/开头，
        // 后为空白/引号/括号/逗号/分号/结尾），避免对英文单词误报——
        // 例如 \bDELETE\b 会误判 "delete@example.com"（@ 构成单词边界）。
        Pattern.compile(
                "(?:^|[\\s\"'\\(,;])(SELECT|INSERT|UPDATE|DELETE|DROP|UNION|ALTER|CREATE|TRUNCATE)"
                        + "(?:\\s|$|[\\s\"'\\);,])",
                Pattern.CASE_INSENSITIVE),
        // 检测 SQL 注释符（--、#、/* */）
        Pattern.compile("(--|#|/\\*|\\*/)", Pattern.CASE_INSENSITIVE),
        // 检测 SQL 特殊字符组合（单引号+分号、分号后跟危险关键字）
        Pattern.compile("('\\s*;|;\\s*(DROP|DELETE|UPDATE|INSERT))", Pattern.CASE_INSENSITIVE)
    };

    /** 路径遍历特征字符串，用于检测目录穿越攻击。 */
    private static final String PATH_TRAVERSAL_PATTERN = "../";

    /** 允许的 Content-Type 前缀列表 */
    private static final List<String> ALLOWED_CONTENT_TYPE_PREFIXES = List.of(
            "application/json",
            "application/x-www-form-urlencoded",
            "multipart/form-data",
            "text/plain",
            "application/xml",
            "text/xml",
            "image/",
            "audio/",
            "video/",
            "application/octet-stream");

    /** 输入验证配置属性，包含检测开关、长度限制和黑名单模式。 */
    private final InputValidationProperties validationProperties;

    /** 国际化消息工具，用于根据客户端语言返回对应文本 */
    private final MessageUtils messageUtils;

    /** 安全指标记录器，记录 XSS/SQL 注入等安全拦截事件。 */
    private final SecurityMetricsRecorder securityMetricsRecorder;

    /**
     * 执行输入验证过滤
     *
     * <p>按顺序执行以下校验步骤：
     * <ol>
     *   <li>路径遍历检测 — 校验请求原始路径</li>
     *   <li>查询参数 XSS 检测 — 遍历所有查询参数值</li>
     *   <li>SQL 注入检测 — 遍历所有查询参数值</li>
     *   <li>参数长度检测 — 校验原始查询字符串长度</li>
     *   <li>Content-Type 验证 — 校验请求头 Content-Type</li>
     *   <li>请求体 XSS/SQL 检测 — 针对 POST/PUT 请求读取请求体</li>
     * </ol>
     * 任一校验失败将直接返回 400 错误响应。
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @param chain    WebFlux 过滤器链
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!validationProperties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        URI uri = request.getURI();

        // 路径遍历检测：使用 getRawPath() 防止 URL 编码绕过
        if (validationProperties.isPathTraversalEnabled()) {
            String rawPath = uri.getRawPath();
            if (rawPath != null && containsPathTraversal(rawPath)) {
                log.warn("检测到路径遍历攻击, URI: {}", LogSafetyUtil.uri(uri.getRawPath(), 256));
                return buildI18nError(exchange, "web.input_path_traversal_detected");
            }
        }

        // 查询参数 XSS 检测：遍历所有查询参数值
        if (validationProperties.isXssEnabled()) {
            for (List<String> values : request.getQueryParams().values()) {
                if (values != null) {
                    for (String value : values) {
                        if (value != null && containsXss(value)) {
                            log.warn("检测到查询参数 XSS 攻击，URI: {}", LogSafetyUtil.uri(uri.getRawPath(), 256));
                            securityMetricsRecorder.recordXssIntercepted();
                            return buildI18nError(exchange, "web.input_xss_detected");
                        }
                    }
                }
            }
        }

        // 查询参数 SQL 注入检测：遍历所有查询参数值
        if (validationProperties.isSqlInjectionPrecheck()) {
            for (List<String> values : request.getQueryParams().values()) {
                if (values != null) {
                    for (String value : values) {
                        if (value != null && hasSqlInjection(value)) {
                            log.warn("检测到查询参数 SQL 注入攻击，URI: {}", LogSafetyUtil.uri(uri.getRawPath(), 256));
                            return buildI18nError(exchange, "web.input_sql_injection_detected");
                        }
                    }
                }
            }
        }

        // 参数长度检测
        String rawQuery = uri.getRawQuery();
        if (rawQuery != null && rawQuery.length() > validationProperties.getMaxParamLength()) {
            log.warn("请求参数超长, URI: {}, 长度: {}", LogSafetyUtil.uri(uri.getRawPath(), 256), rawQuery.length());
            return buildI18nError(exchange, "web.input_param_too_long");
        }

        // Content-Type 验证
        MediaType contentType = request.getHeaders().getContentType();
        if (contentType != null && !isContentTypeAllowed(contentType)) {
            log.warn(
                    "Content-Type 不在白名单中, Content-Type: {}, URI: {}",
                    LogSafetyUtil.text(contentType.toString(), 128),
                    LogSafetyUtil.uri(uri.getRawPath(), 256));
            return buildI18nError(exchange, "web.input_content_type_blocked");
        }

        // 仅对 POST/PUT 请求执行请求体 XSS/SQL 检测，multipart 请求跳过
        if (shouldValidateRequestBody(request)) {
            return filterRequestBody(exchange, chain);
        }

        return chain.filter(exchange);
    }

    /**
     * 判断是否需要对请求体进行安全检测
     *
     * <p>仅当 XSS 检测或 SQL 注入预检启用，且请求方法需要读取请求体时才进行检测。
     * multipart/form-data 请求体包含二进制文件内容，XSS/SQL 文本检测无意义，
     * 且缓存重建请求体会导致下游 WebFlux multipart 解析器无法正确解析，因此跳过。
     *
     * @param request 当前 HTTP 请求
     * @return 是否需要检测请求体
     */
    private boolean shouldValidateRequestBody(ServerHttpRequest request) {
        if (!validationProperties.isXssEnabled() && !validationProperties.isSqlInjectionPrecheck()) {
            return false;
        }
        if (!isBodyReadingRequired(request.getMethod())) {
            return false;
        }
        MediaType contentType = request.getHeaders().getContentType();
        if (contentType != null && contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA)) {
            return false;
        }
        return true;
    }

    /**
     * 过滤请求体中的恶意攻击特征
     *
     * <p>读取请求体全部字节并解码为 UTF-8 字符串，检测是否包含 XSS 攻击特征或 SQL 注入特征。
     * 检测通过后，通过 {@link CachedBodyExchangeDecorator} 缓存请求体字节，
     * 确保下游 Handler 和 Filter 能够正常读取请求体内容。
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @param chain    WebFlux 过滤器链
     */
    private Mono<Void> filterRequestBody(ServerWebExchange exchange, WebFilterChain chain) {
        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(bufferFactory.wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    if (bytes.length > 0) {
                        String body = new String(bytes, StandardCharsets.UTF_8);
                        if (validationProperties.isXssEnabled() && containsXss(body)) {
                            log.warn(
                                    "检测到请求体 XSS 攻击, URI: {}",
                                    LogSafetyUtil.uri(
                                            exchange.getRequest().getURI().getRawPath(), 256));
                            securityMetricsRecorder.recordXssIntercepted();
                            return buildI18nError(exchange, "web.input_xss_detected");
                        }
                        if (validationProperties.isSqlInjectionPrecheck() && hasSqlInjection(body)) {
                            log.warn(
                                    "检测到请求体 SQL 注入攻击, URI: {}",
                                    LogSafetyUtil.uri(
                                            exchange.getRequest().getURI().getRawPath(), 256));
                            return buildI18nError(exchange, "web.input_sql_injection_detected");
                        }
                    }

                    // 构造缓存请求体的代理交换对象，下游可重复读取请求体
                    CachedBodyExchangeDecorator decorator =
                            new CachedBodyExchangeDecorator(exchange, bytes, bufferFactory);
                    return chain.filter(decorator);
                });
    }

    /**
     * 判断 HTTP 方法是否需要读取请求体
     *
     * <p>仅 POST、PUT、PATCH 方法需要读取请求体进行 XSS 检测，
     * GET、DELETE、HEAD、OPTIONS 等方法无需读取请求体。
     *
     * @param method HTTP 请求方法
     * @return 如果需要读取请求体返回 true
     */
    private boolean isBodyReadingRequired(HttpMethod method) {
        if (method == null) {
            return false;
        }
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }

    /**
     * 检测字符串是否包含路径遍历特征
     *
     * <p>检测字符串中是否包含 {@code ../} 路径遍历特征，用于防止目录穿越攻击。
     *
     * @param value 待检测的字符串
     * @return 如果包含路径遍历特征返回 true
     */
    private boolean containsPathTraversal(String value) {
        if (value == null) {
            return false;
        }
        return value.contains(PATH_TRAVERSAL_PATTERN);
    }

    /**
     * 检测字符串是否包含 XSS 攻击特征
     *
     * <p>使用预编译的正则表达式模式对输入字符串进行匹配检测。
     * 遍历所有 XSS 攻击特征模式，任一模式匹配即判定为存在攻击特征。
     *
     * @param value 待检测的字符串
     * @return 如果包含 XSS 攻击特征返回 true
     */
    private boolean containsXss(String value) {
        if (value == null) {
            return false;
        }
        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(value).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检测字符串是否包含 SQL 注入特征
     *
     * <p>遍历 SQL 注入特征正则列表，若任一模式匹配成功则判定为 SQL 注入攻击。
     * 检测范围包括：
     * <ul>
     *   <li>SQL 关键字：SELECT、INSERT、UPDATE、DELETE、DROP、UNION、ALTER、CREATE、TRUNCATE</li>
     *   <li>SQL 注释符：{@code --}、{@code #}、斜线星号 及 星号斜线</li>
     *   <li>SQL 特殊字符组合：单引号 + 空格 + 分号、分号后跟危险关键字</li>
     * </ul>
     *
     * @param value 待检测的字符串值
     * @return 如果检测到 SQL 注入特征返回 true
     */
    private boolean hasSqlInjection(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        for (Pattern pattern : SQL_INJECTION_PATTERNS) {
            if (pattern.matcher(value).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 Content-Type 是否在允许列表中
     *
     * <p>使用前缀匹配方式校验 Content-Type，支持通配格式。
     * 例如 {@code image/} 前缀可以匹配 {@code image/png}、{@code image/jpeg} 等。
     *
     * @param contentType 请求的 Content-Type
     * @return 如果在允许列表中返回 true
     */
    private boolean isContentTypeAllowed(MediaType contentType) {
        if (contentType == null) {
            return true;
        }
        String type = contentType.toString().toLowerCase();
        for (String allowed : ALLOWED_CONTENT_TYPE_PREFIXES) {
            if (allowed.endsWith("/")) {
                if (type.startsWith(allowed)) {
                    return true;
                }
            } else {
                if (type.startsWith(allowed)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 缓存请求体的服务端交换对象装饰器
     *
     * <p>由于请求体在被 {@link DataBufferUtils#join} 读取后无法再次读取，
     * 此装饰器通过 {@link CachedBodyServerHttpRequest} 缓存请求体字节，
     * 使下游 Filter 和 Handler 能够正常通过 {@code getBody()} 获取请求体数据。
     *
     * @author Apex Team
     * @version 1.0.0
     * @since 2.0.0
     */
    private static class CachedBodyExchangeDecorator extends ServerWebExchangeDecorator {

        /** 缓存了请求体的请求装饰器 */
        private final CachedBodyServerHttpRequest cachedRequest;

        /**
         * 构造一个缓存请求体的交换对象装饰器
         *
         * @param delegate     原始服务端交换对象
         * @param bodyBytes    请求体字节数组
         * @param bufferFactory DataBuffer 工厂
         */
        CachedBodyExchangeDecorator(ServerWebExchange delegate, byte[] bodyBytes, DataBufferFactory bufferFactory) {
            super(delegate);
            this.cachedRequest = new CachedBodyServerHttpRequest(delegate.getRequest(), bodyBytes, bufferFactory);
        }

        /**
         * 获取缓存了请求体的请求对象
         *
         * @return 缓存了请求体的 {@link ServerHttpRequest}
         */
        @Override
        public ServerHttpRequest getRequest() {
            return cachedRequest;
        }
    }

    /**
     * 缓存请求体的 HTTP 请求装饰器
     *
     * <p>将请求体字节数组包装为 {@link DataBuffer}，使 {@link #getBody()} 方法
     * 能够被多次订阅读取。适用于 WebFlux 中请求体被消费后需要再次读取的场景。
     *
     * @author Apex Team
     * @version 1.0.0
     * @since 2.0.0
     */
    private static class CachedBodyServerHttpRequest extends ServerHttpRequestDecorator {

        /** 缓存的请求体字节数组 */
        private final byte[] bodyBytes;

        /** DataBuffer 工厂，用于创建包装请求体的 DataBuffer */
        private final DataBufferFactory bufferFactory;

        /**
         * 构造一个缓存请求体的请求装饰器
         *
         * @param delegate      原始 HTTP 请求
         * @param bodyBytes     缓存的请求体字节数组
         * @param bufferFactory DataBuffer 工厂
         */
        CachedBodyServerHttpRequest(ServerHttpRequest delegate, byte[] bodyBytes, DataBufferFactory bufferFactory) {
            super(delegate);
            this.bodyBytes = bodyBytes;
            this.bufferFactory = bufferFactory;
        }

        /**
         * 获取请求头
         *
         * <p>当缓存了空请求体时，移除 {@code Content-Length} 头，
         * 避免下游 Handler 因 Content-Length 与实际体大小不匹配而报错。
         *
         * @return HTTP 请求头
         */
        @Override
        public HttpHeaders getHeaders() {
            if (bodyBytes.length == 0) {
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(super.getHeaders());
                headers.remove(HttpHeaders.CONTENT_LENGTH);
                return HttpHeaders.readOnlyHttpHeaders(headers);
            }
            return super.getHeaders();
        }

        /**
         * 获取缓存的请求体数据流
         *
         * <p>每次调用均返回包含缓存字节数组的新 {@link Flux}，
         * 支持多次订阅读取。
         *
         * @return 包含请求体数据的 {@link Flux}{@code <DataBuffer>}
         */
        @Override
        public Flux<DataBuffer> getBody() {
            if (bodyBytes.length == 0) {
                return Flux.empty();
            }
            DataBuffer buffer = bufferFactory.wrap(bodyBytes);
            return Flux.just(buffer);
        }
    }
}
