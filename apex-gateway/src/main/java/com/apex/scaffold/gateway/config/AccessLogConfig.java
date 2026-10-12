package com.apex.scaffold.gateway.config;

import com.apex.scaffold.gateway.log.AccessLogProperties;
import com.apex.scaffold.gateway.log.AccessLogRepository;
import com.apex.scaffold.gateway.log.AccessLogService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关访问日志落库配置.
 *
 * <p>装配访问日志持久化链路：将 {@link AccessLogProperties} 绑定为可注入 Bean，
 * 并以 {@link AccessLogService} 作为落库编排的<b>唯一入口</b>。
 *
 * <p><b>为何用 {@code @Bean} 而非组件扫描</b>：{@link AccessLogService} 为纯领域类
 * （不含 {@code @Component}），需显式注册；同时网关入口
 * {@link com.apex.scaffold.gateway.GatewayApplication} 未开启
 * {@code @ConfigurationPropertiesScan}，故此处以
 * {@link EnableConfigurationProperties} 显式绑定 {@link AccessLogProperties}。
 *
 * <p><b>依赖方向</b>：{@code config → log}，过滤器仅依赖 {@code log} 包类型
 * （由 {@code GatewayArchTest} 约束过滤器不得依赖 {@code config} 包）。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration
@EnableConfigurationProperties(AccessLogProperties.class)
public class AccessLogConfig {

    /**
     * 注册访问日志落库服务.
     *
     * <p>仓储实现 {@link com.apex.scaffold.gateway.log.R2dbcAccessLogRepository}
     * 由组件扫描注册为 {@link AccessLogRepository}，此处直接注入。
     *
     * @param repository 访问日志仓储（R2DBC 实现）
     * @param properties 访问日志落库配置
     * @return 访问日志落库服务
     */
    @Bean
    public AccessLogService accessLogService(
            AccessLogRepository repository, AccessLogProperties properties, MeterRegistry meterRegistry) {
        return new AccessLogService(repository, properties, meterRegistry);
    }
}
