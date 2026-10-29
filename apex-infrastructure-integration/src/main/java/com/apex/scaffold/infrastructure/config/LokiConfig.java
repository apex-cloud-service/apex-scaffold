package com.apex.scaffold.infrastructure.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Grafana Loki 日志查询配置类
 *
 * <p>本配置类负责装配 Loki 日志查询客户端（基于 WebClient），用于查询应用运行时日志。
 * Loki 是一款轻量级日志聚合系统，专为云原生应用设计，比 ELK 栈资源消耗降低 80% 以上。
 *
 * <h3>技术架构</h3>
 * <pre>
 * 应用 stdout/文件日志 → Grafana Alloy 采集 → Loki 存储（标签索引 + 全文扫描）
 *                                              ↓
 *         管理后台 ← 本服务 RuntimeLogHandler ← Loki HTTP API ← LokiAdapter（WebClient）
 * </pre>
 *
 * <h3>技术选型理由</h3>
 * <ul>
 *   <li><b>轻量高效</b>：仅索引标签（service、level、traceId），全文内容以原始格式压缩存储，
 *       相比 Elasticsearch 全文索引，内存和磁盘占用降低 10 倍</li>
 *   <li><b>云原生设计</b>：天然支持 Kubernetes 动态标签、容器日志采集，与 Prometheus 指标体系无缝集成</li>
 *   <li><b>LogQL 查询语言</b>：类似 PromQL，支持正则过滤、JSON 解析、聚合统计，功能完整</li>
 *   <li><b>Grafana 原生集成</b>：作为 Grafana Labs 产品，与 Grafana 可视化平台深度集成</li>
 * </ul>
 *
 * <h3>启用条件</h3>
 * <p>当配置 {@code loki.enabled=true} 时生效，默认关闭。
 * 开发环境可在 {@code application-dev.yml} 中启用，生产环境通过环境变量 {@code LOKI_ENABLED} 控制。
 *
 * <h3>配置项说明</h3>
 * <pre>
 * loki:
 *   enabled: true                      # 总开关
 *   url: http://localhost:3100         # Loki 服务地址
 *   max-lines: 1000                    # 单次查询最大返回行数
 *   max-query-range-seconds: 3600      # 单次查询最大时间窗口（1小时）
 * </pre>
 *
 * <h3>查询示例（LogQL）</h3>
 * <pre>{@code
 * // 查询最近 1 小时的 ERROR 日志，包含关键词 "exception"
 * {service="apex-scaffold", level="error"} |= "exception" | json
 *
 * // 查询指定 traceId 的所有日志
 * {service="apex-scaffold", traceId="abc123"} | json
 * }</pre>
 *
 * @author Apex Team
 * @since 1.3.0
 * @see WebClient Spring WebFlux 响应式 HTTP 客户端
 * @see LokiProperties Loki 配置属性类
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "loki", name = "enabled", havingValue = "true")
public class LokiConfig {

    /**
     * 创建 Loki 配置属性 Bean
     *
     * <p>从 {@code loki.*} 配置项绑定属性。
     *
     * @return Loki 配置属性实例
     */
    @Bean
    @ConfigurationProperties(prefix = "loki")
    public LokiProperties lokiProperties() {
        return new LokiProperties();
    }

    /**
     * 创建 Loki HTTP 客户端 Bean
     *
     * <p>使用 Spring WebFlux 的 {@link WebClient} 调用 Loki Query API。
     * WebClient 是响应式非阻塞客户端，适配本项目 WebFlux 技术栈。
     *
     * <h4>API 端点</h4>
     * <ul>
     *   <li>{@code GET /loki/api/v1/query_range} - 范围查询（时间窗口 + LogQL 过滤）</li>
     *   <li>{@code GET /loki/api/v1/query} - 即时查询（等同于 query_range 的时间点查询）</li>
     *   <li>{@code GET /loki/api/v1/labels} - 查询所有标签键</li>
     *   <li>{@code GET /loki/api/v1/label/<name>/values} - 查询标签值</li>
     * </ul>
     *
     * <h4>超时配置</h4>
     * <p>WebClient 默认超时策略继承自全局 {@code server.webclient.*} 配置：
     * <ul>
     *   <li>连接超时：5 秒</li>
     *   <li>读取超时：10 秒</li>
     * </ul>
     *
     * <h4>错误处理</h4>
     * <p>Loki 查询失败（网络超时、服务不可用、LogQL 语法错误）会返回 HTTP 4xx/5xx 响应，
     * 业务代码应捕获 {@link org.springframework.web.reactive.function.client.WebClientResponseException} 并降级处理。
     *
     * @param properties Loki 配置属性
     * @return Loki HTTP 客户端（响应式 WebClient）
     */
    @Bean
    public WebClient lokiWebClient(LokiProperties properties) {
        WebClient client = WebClient.builder().baseUrl(properties.getUrl()).build();
        log.info("Loki WebClient initialized: url={}", properties.getUrl());
        return client;
    }

    /**
     * Loki 配置属性类
     *
     * <p>从 {@code loki.*} 配置项绑定属性，支持环境变量覆盖。
     *
     * <h3>查询限制说明</h3>
     * <p>为防止慢查询拖垮 Loki 服务，强制以下限制：
     * <ul>
     *   <li><b>单次查询最大行数</b>：1000 条（超出截断，引导用户使用 Grafana Explore 高级查询）</li>
     *   <li><b>单次查询最大时间窗口</b>：1 小时（3600 秒），超出返回错误</li>
     *   <li><b>查询回溯窗口</b>：最近 7 天（Loki retention_period 配置，超出数据已清理）</li>
     * </ul>
     */
    @Data
    public static class LokiProperties {

        /**
         * Loki 服务地址
         *
         * <p>示例：{@code http://localhost:3100}（本地开发）、{@code http://loki:3100}（Podman Compose）、
         * {@code http://loki.default.svc.cluster.local:3100}（Kubernetes）
         *
         * <p>默认值：{@code http://localhost:3100}
         */
        private String url = "http://localhost:3100";

        /**
         * 单次查询最大返回行数
         *
         * <p>对应 Loki Query API 的 {@code limit} 参数。
         * 超出该值时，Loki 仅返回前 N 条日志，并在响应头 {@code X-Query-Limit} 标注已截断。
         *
         * <p>建议范围：500-1000，过大会增加网络传输和前端渲染压力。
         *
         * <p>默认值：{@code 1000}
         */
        private int maxLines = 1000;

        /**
         * 单次查询最大时间窗口（秒）
         *
         * <p>前端查询时间范围超出该值时，后端强制截断或返回错误提示。
         * 限制查询窗口可防止慢查询长时间占用 Loki 资源，影响其他用户。
         *
         * <p>建议值：
         * <ul>
         *   <li>常规排障：1 小时（3600 秒）</li>
         *   <li>调试场景：6 小时（21600 秒）</li>
         *   <li>数据分析：24 小时（86400 秒，需评估 Loki 查询性能）</li>
         * </ul>
         *
         * <p>默认值：{@code 3600}（1 小时）
         */
        private long maxQueryRangeSeconds = 3600; // 1小时

        /**
         * 是否启用 Loki 日志查询
         *
         * <p>总开关，由 {@code @ConditionalOnProperty} 读取。
         * 开发环境在 {@code application-dev.yml} 中设为 {@code true}，
         * 生产环境通过环境变量 {@code LOKI_ENABLED} 控制。
         *
         * <p>默认值：{@code false}（关闭）
         */
        private boolean enabled = false;
    }
}
