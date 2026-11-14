package com.apex.scaffold.infrastructure.log;

import com.apex.scaffold.domain.log.RuntimeLogEntry;
import com.apex.scaffold.domain.log.RuntimeLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * 运行时日志查询关闭时的降级装配。
 *
 * <p>{@link LokiAdapter} 依赖 {@code LokiConfig.LokiProperties}，该属性 Bean 仅在
 * {@code loki.enabled=true} 时注册。未启用 Loki 的环境必须仍有
 * {@link RuntimeLogRepository} 实现，否则日志查询服务与 Handler 会因缺少依赖导致
 * 上下文启动失败。本配置在缺少实现时返回空结果，语义为「功能未启用」。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class LogQueryDisabledConfiguration {

    /**
     * 未启用 Loki 时的空日志查询实现。
     *
     * @return 返回空结果的运行时日志仓库
     */
    @Bean
    @ConditionalOnMissingBean(RuntimeLogRepository.class)
    public RuntimeLogRepository disabledRuntimeLogRepository() {
        log.info("运行时日志查询未启用（loki.enabled=false）：日志查询返回空结果");
        return new RuntimeLogRepository() {
            @Override
            public Mono<List<RuntimeLogEntry>> queryLogs(
                    LocalDateTime startTime,
                    LocalDateTime endTime,
                    String level,
                    String logger,
                    String message,
                    int limit) {
                return Mono.just(List.of());
            }

            @Override
            public Mono<Long> countLogs(LocalDateTime startTime, LocalDateTime endTime, String level) {
                return Mono.just(0L);
            }
        };
    }
}
