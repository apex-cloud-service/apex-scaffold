package com.apex.scaffold.interfaces.config;

import brave.Span;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * 链路追踪 Span 日志处理器，记录完整的 Span 生命周期信息。
 *
 * <p>在每个 Span 完成时将结构化详情写入日志，路由到 ZIPKIN_FILE appender。
 * 与生产环境上报 Zipkin 解耦，仅增强本地开发的可观测性。</p>
 *
 * <h2>记录的字段</h2>
 * <table>
 *   <tr><th>类别</th><th>字段</th><th>说明</th></tr>
 *   <tr><td rowspan="3">标识</td><td>traceId</td><td>全链路追踪 ID</td></tr>
 *   <tr><td>spanId</td><td>当前 Span ID</td></tr>
 *   <tr><td>parentId</td><td>父 Span ID（根 Span 为空）</td></tr>
 *   <tr><td rowspan="2">基本信息</td><td>name</td><td>Span 名称（如 GET /api/v1/xxx）</td></tr>
 *   <tr><td>kind</td><td>Span 类型（CLIENT/SERVER/PRODUCER/CONSUMER）</td></tr>
 *   <tr><td rowspan="3">时间</td><td>start</td><td>Span 开始时间（ISO-8601）</td></tr>
 *   <tr><td>finish</td><td>Span 结束时间（ISO-8601）</td></tr>
 *   <tr><td>durationMs</td><td>Span 持续时间（ms）</td></tr>
 *   <tr><td rowspan="2">端点</td><td>local</td><td>本地服务名与 IP:Port</td></tr>
 *   <tr><td>remote</td><td>远程服务名与 IP:Port</td></tr>
 *   <tr><td>HTTP</td><td>http</td><td>method + path + status_code</td></tr>
 *   <tr><td>标签</td><td>tags</td><td>全部自定义标签（按 key 排序）</td></tr>
 *   <tr><td>注解</td><td>annotations</td><td>全部时间点注解（sr/ss/cs/cr 等）</td></tr>
 *   <tr><td>错误</td><td>error</td><td>错误标签或真实异常信息</td></tr>
 * </table>
 *
 * <p>记录级别分级：真实异常或 5xx 记为 ERROR（同时进入 error.log），
 * 超过慢请求阈值记为 WARN，4xx 客户端错误记为 WARN，其余记为 INFO。</p>
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Slf4j
public class TraceLoggingSpanHandler extends SpanHandler {

    /** 慢请求判定阈值（毫秒），超过该值的 Span 单独以 WARN 级别记录 */
    private final long slowThresholdMs;

    /** 时间格式化器：将毫秒时间戳转换为可读的时间字符串 */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    /**
     * 构造 Span 日志处理器。
     *
     * @param slowThresholdMs 慢请求阈值（毫秒），由配置注入
     */
    public TraceLoggingSpanHandler(long slowThresholdMs) {
        this.slowThresholdMs = slowThresholdMs;
    }

    /**
     * Span 完成后回调：提取并记录 Span 完整详情。
     *
     * @param context 追踪上下文（含 traceId/spanId/parentId）
     * @param span    待完成的 MutableSpan
     * @param cause   完成原因（FINISHED / FLUSHED / ABANDONED / ORPHANED）
     * @return true 表示继续传递给下一个 SpanHandler
     */
    @Override
    public boolean end(TraceContext context, MutableSpan span, Cause cause) {
        SpanSummary.of(span, cause).log(slowThresholdMs);
        return true;
    }

    /**
     * Span 摘要：将 {@link MutableSpan} 的原始数据收敛为一条结构化的日志记录。
     *
     * <p>以 record 承载不可变快照，配合 Stream / Optional 完成字段提取与渲染，
     * 保证日志输出为单行 key=value 形式，便于检索与统计。</p>
     *
     * @param traceId        全链路追踪 ID
     * @param spanId         当前 Span ID
     * @param parentId       父 Span ID
     * @param name           Span 名称
     * @param kind           Span 类型
     * @param cause          Span 完成原因
     * @param startEpochMs   开始时间（毫秒）
     * @param finishEpochMs  结束时间（毫秒）
     * @param durationMs     持续时间（毫秒）
     * @param localEndpoint  本地端点（服务名 + IP:Port）
     * @param remoteEndpoint 远程端点（服务名 + IP:Port）
     * @param tags           自定义标签集合
     * @param annotations    时间点注解明细
     * @param error          真实异常（可能为空）
     */
    record SpanSummary(
            String traceId,
            String spanId,
            String parentId,
            String name,
            Span.Kind kind,
            Cause cause,
            long startEpochMs,
            long finishEpochMs,
            long durationMs,
            String localEndpoint,
            String remoteEndpoint,
            Map<String, String> tags,
            String annotations,
            Throwable error) {

        /**
         * 从 {@link MutableSpan} 提取全部字段构建摘要。
         *
         * @param span  待处理的 Span
         * @param cause 完成原因
         * @return 构建完成的 SpanSummary
         */
        static SpanSummary of(MutableSpan span, Cause cause) {
            long startMicros = span.startTimestamp();
            long finishMicros = span.finishTimestamp();
            return new SpanSummary(
                    span.traceId(),
                    span.id(),
                    span.parentId(),
                    span.name(),
                    span.kind(),
                    cause,
                    microsToMillis(startMicros),
                    microsToMillis(finishMicros),
                    (finishMicros - startMicros) / 1000,
                    formatEndpoint(span.localServiceName(), span.localIp(), span.localPort()),
                    formatEndpoint(span.remoteServiceName(), span.remoteIp(), span.remotePort()),
                    span.tags(),
                    renderAnnotations(span),
                    span.error());
        }

        /**
         * 按分级规则记录摘要日志，异常作为最后一个参数附加堆栈。
         *
         * @param slowThresholdMs 慢请求阈值（毫秒）
         */
        void log(long slowThresholdMs) {
            switch (levelOf(slowThresholdMs)) {
                case ERROR -> TraceLoggingSpanHandler.log.error("[Span-错误] {}", render(), error);
                case SLOW -> TraceLoggingSpanHandler.log.warn("[Span-慢] {}", render());
                case CLIENT_ERROR -> TraceLoggingSpanHandler.log.warn("[Span-警告] {}", render());
                case NORMAL -> TraceLoggingSpanHandler.log.info("[Span] {}", render());
            }
        }

        /**
         * 计算日志级别：真实异常或 5xx 为 ERROR，超过阈值标记为慢请求，
         * 4xx 客户端错误单独标记为警告，其余为 INFO。
         *
         * @param slowThresholdMs 慢请求阈值（毫秒）
         * @return 对应的日志级别
         */
        private LogLevel levelOf(long slowThresholdMs) {
            boolean hasException = error != null;
            boolean serverError = isStatusPrefix("5");
            boolean slow = durationMs >= slowThresholdMs;
            boolean clientError = isStatusPrefix("4");
            if (hasException || serverError) {
                return LogLevel.ERROR;
            }
            if (slow) {
                return LogLevel.SLOW;
            }
            if (clientError) {
                return LogLevel.CLIENT_ERROR;
            }
            return LogLevel.NORMAL;
        }

        /**
         * 判断 HTTP 状态码是否以指定前缀开头。
         *
         * @param prefix 前缀（如 "4" 表示 4xx，"5" 表示 5xx）
         * @return 匹配返回 true
         */
        private boolean isStatusPrefix(String prefix) {
            return Optional.ofNullable(tags.get("http.status_code"))
                    .filter(status -> status.startsWith(prefix))
                    .isPresent();
        }

        /**
         * 将摘要渲染为单行 key=value 文本。
         *
         * @return 渲染后的日志文本
         */
        private String render() {
            return Stream.of(
                            "traceId=" + traceId,
                            "spanId=" + spanId,
                            "parentId=" + Optional.ofNullable(parentId).orElse("-"),
                            "name=" + Optional.ofNullable(name).orElse("-"),
                            "kind=" + Optional.ofNullable(kind).orElse(Span.Kind.SERVER),
                            "cause=" + Optional.ofNullable(cause).orElse(Cause.FINISHED),
                            "start=" + formatTime(startEpochMs),
                            "finish=" + formatTime(finishEpochMs),
                            "durationMs=" + durationMs,
                            "local=" + localEndpoint,
                            "remote=" + remoteEndpoint,
                            "http=" + renderHttp(),
                            "tags=" + renderTags(),
                            "annotations=" + annotations,
                            "error=" + renderError())
                    .collect(Collectors.joining(" "));
        }

        /**
         * 渲染 HTTP 摘要：method + path + status_code。
         *
         * @return 如 "GET /api/v1/battles 200"
         */
        private String renderHttp() {
            return Stream.of(
                            Optional.ofNullable(tags.get("http.method")).orElse("-"),
                            Optional.ofNullable(tags.get("http.path")).orElse("-"),
                            Optional.ofNullable(tags.get("http.status_code")).orElse("-"))
                    .collect(Collectors.joining(" "));
        }

        /**
         * 渲染全部标签：按 key 排序后的 [k=v,...] 列表。
         *
         * @return 排序后的标签明细
         */
        private String renderTags() {
            return Optional.ofNullable(tags)
                    .map(Map::entrySet)
                    .map(entries -> entries.stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .collect(Collectors.joining(",", "[", "]")))
                    .orElse("[]");
        }

        /**
         * 渲染错误信息：优先使用 error 标签，否则使用真实异常的类名与消息。
         *
         * @return 错误描述
         */
        private String renderError() {
            return Optional.ofNullable(tags.get("error"))
                    .orElseGet(() ->
                            Optional.ofNullable(error).map(Throwable::toString).orElse("-"));
        }

        /**
         * 渲染 Span 的时间点注解：按时间戳升序的 [time(value),...] 列表。
         *
         * @param span 待处理的 Span
         * @return 注解明细
         */
        private static String renderAnnotations(MutableSpan span) {
            return span.annotations().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> formatTime(microsToMillis(entry.getKey())) + "(" + entry.getValue() + ")")
                    .collect(Collectors.joining(",", "[", "]"));
        }

        /**
         * 构建端点描述：优先展示服务名，并附加 IP:Port。
         *
         * @param serviceName 服务名（可能为空）
         * @param ip          服务 IP（可能为空）
         * @param port        服务端口（0 表示未知）
         * @return 如 "apex-scaffold (127.0.0.1:8080)" 或 "N/A"
         */
        private static String formatEndpoint(String serviceName, String ip, int port) {
            String address = Optional.ofNullable(ip)
                    .filter(value -> !value.isBlank())
                    .map(value -> port > 0 ? value + ":" + port : value)
                    .orElse("N/A");
            return Optional.ofNullable(serviceName)
                    .map(name -> name + " (" + address + ")")
                    .orElse(address);
        }

        /**
         * 将毫秒时间戳格式化为可读时间，0 或负数表示未知。
         *
         * @param epochMs 毫秒时间戳
         * @return 格式化后的时间字符串
         */
        private static String formatTime(long epochMs) {
            return Optional.of(epochMs)
                    .filter(ms -> ms > 0)
                    .map(Instant::ofEpochMilli)
                    .map(TIME_FORMATTER::format)
                    .orElse("-");
        }

        /**
         * 微秒转毫秒。
         *
         * @param micros 微秒时间戳
         * @return 毫秒时间戳
         */
        private static long microsToMillis(long micros) {
            return micros / 1000;
        }
    }

    /** 日志级别分类 */
    private enum LogLevel {
        /** 真实异常或 5xx 服务端错误 */
        ERROR,
        /** 超过慢请求阈值 */
        SLOW,
        /** 4xx 客户端错误 */
        CLIENT_ERROR,
        /** 正常请求 */
        NORMAL
    }
}
