package com.apex.scaffold.interfaces.config;

import brave.Tracing;
import brave.context.slf4j.MDCScopeDecorator;
import brave.handler.SpanHandler;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.brave.ZipkinSpanHandler;
import zipkin2.reporter.okhttp3.OkHttpSender;

/**
 * 全链路追踪配置类
 *
 * <p>配置Brave链路追踪，支持Zipkin上报，提供分布式系统中的请求全链路追踪能力
 *
 * <h2>设计模式</h2>
 * <ul>
 *   <li><strong>配置模式</strong>：通过外部配置文件动态控制采样率和Zipkin上报地址</li>
 *   <li><strong>观察者模式</strong>：通过 {@link SpanHandler} 和 {@link AsyncReporter} 异步上报链路数据</li>
 *   <li><strong>装饰器模式</strong>：通过 {@link MDCScopeDecorator} 将TraceId注入MDC日志上下文</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class TraceConfig {

    /**
     * 构造全链路追踪配置类
     *
     * <p>由 Spring 容器自动实例化，无需手动创建
     */
    public TraceConfig() {}

    /** 服务名称，用于链路追踪标识 */
    @Value("${spring.application.name:apex-service}")
    private String serviceName;

    /** 采样率，范围 0.0-1.0，表示采样的比例 */
    @Value("${trace.sampling-rate:1.0}")
    private float samplingRate;

    /** Zipkin 服务器地址，用于上报链路追踪数据 */
    @Value("${trace.zipkin-url:http://localhost:9411/api/v2/spans}")
    private String zipkinUrl;

    /** 慢请求阈值（毫秒），超过该值的 Span 以 WARN 级别记录详情 */
    @Value("${trace.slow-threshold-ms:3000}")
    private long slowThresholdMs;

    /** Tracing 实例，用于在 @PreDestroy 中关闭 */
    private Tracing tracing;

    /**
     * 配置 Zipkin Span 上报处理器（所有环境启用）。
     *
     * <p>通过 {@code trace.zipkin-url} 指定 Zipkin 服务地址，
     * 本地开发可通过 SSH 隧道或 VPN 访问远程 Zipkin 管理界面。
     * 若远程 Zipkin 不可达，上报失败仅记录日志，不影响主业务。
     *
     * @return ZipkinSpanHandler 实例
     */
    @Bean
    @Primary
    public SpanHandler zipkinSpanHandler() {
        log.info("Zipkin 链路上报已启用, 地址: {}", zipkinUrl);
        OkHttpSender sender = OkHttpSender.create(zipkinUrl);
        Reporter<Span> reporter = AsyncReporter.builder(sender).build();
        return ZipkinSpanHandler.create(reporter);
    }

    /**
     * 配置链路追踪。
     *
     * <p>创建并初始化 Brave 的 {@link Tracing} 实例。始终注册
     * {@link TraceLoggingSpanHandler} 将 Span 摘要写入本地日志，
     * 同时通过 {@link ObjectProvider} 注入 {@link ZipkinSpanHandler}，
     * 将 Span 上报到远程 Zipkin 服务器。
     *
     * <h2>配置项</h2>
     * <ul>
     *   <li>serviceName：服务名称，用于在 Zipkin 中标识服务</li>
     *   <li>samplingRate：采样率，控制链路数据的采样比例</li>
     *   <li>zipkinUrl：Zipkin 服务器地址（通过 {@code trace.zipkin-url} 配置）</li>
     * </ul>
     *
     * @param zipkinHandlerProvider ZipkinSpanHandler 的延迟注入
     * @return Tracing 实例
     */
    @Bean
    @SuppressWarnings("deprecation")
    public Tracing tracing(ObjectProvider<SpanHandler> zipkinHandlerProvider) {
        log.info(
                "初始化链路追踪配置, serviceName: {}, samplingRate: {}, slowThresholdMs: {}, zipkinUrl: {}",
                serviceName,
                samplingRate,
                slowThresholdMs,
                zipkinUrl);

        Sampler sampler = Sampler.create(samplingRate);

        CurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
                .addScopeDecorator(MDCScopeDecorator.get())
                .build();

        Tracing.Builder builder = Tracing.newBuilder()
                .localServiceName(serviceName)
                .sampler(sampler)
                .currentTraceContext(currentTraceContext)
                .addSpanHandler(new TraceLoggingSpanHandler(slowThresholdMs));

        zipkinHandlerProvider.ifAvailable(builder::addSpanHandler);

        tracing = builder.build();
        return tracing;
    }

    /**
     * 销毁Tracing实例
     *
     * <p>在应用关闭时释放链路追踪资源，防止内存泄漏
     */
    @PreDestroy
    public void destroy() {
        if (tracing != null) {
            tracing.close();
        }
    }

    /**
     * 配置Brave的Tracer
     *
     * <p>基于已初始化的 {@link Tracing} 实例创建 {@link brave.Tracer}，
     * 用于在业务代码中手动创建自定义Span
     *
     * @param tracing Tracing实例
     * @return brave.Tracer实例
     */
    @Bean
    public brave.Tracer tracer(Tracing tracing) {
        return tracing.tracer();
    }
}
