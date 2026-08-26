package com.apex.scaffold.infrastructure.remote.config;

import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

/**
 * ManagedWebClient 日志过滤器自动配置。
 *
 * <p>在 Spring 容器启动后自动将 {@link ApiCallLogService} 注入
 * {@link ManagedWebClientFactory}，使通过该工厂创建的 WebClient（用于微信 API 调用）
 * 自动记录每次 HTTP 调用的元数据。</p>
 *
 * <h2>启用条件</h2>
 * <p>仅在 classpath 中存在 {@link ApiCallLogService} 时生效（即 apex-application 模块已引入）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ManagedWebClientFactory#enableLogFilter(ApiCallLogService)
 * @see ApiCallLogService
 */
@Configuration
@ConditionalOnClass(ApiCallLogService.class)
public class ManagedWebClientLogConfig {
    private static final Logger log = LoggerFactory.getLogger(ManagedWebClientLogConfig.class);

    /** API 调用日志服务。 */
    private final ApiCallLogService logService;

    /**
     * 构造配置类。
     *
     * @param logService API 调用日志服务
     */
    public ManagedWebClientLogConfig(ApiCallLogService logService) {
        this.logService = logService;
    }

    /**
     * 容器启动后将日志服务注入 ManagedWebClientFactory。
     */
    @PostConstruct
    /**
     * 初始化
     */
    public void init() {
        ManagedWebClientFactory.enableLogFilter(logService);
        log.info("ManagedWebClientFactory API调用日志过滤器已激活");
    }
}
