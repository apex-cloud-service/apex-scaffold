package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 投票性能基准 profile 配置防回归测试。
 *
 * <p>性能基准必须与日常开发诊断模式隔离，确保二选一和四选二优化前后的
 * P50/P95/P99 在相同 P6Spy、日志和 Trace 条件下可比较。</p>
 *
 * @author Apex Team
 * @since 3.3.0
 */
@DisplayName("投票性能基准 profile 配置")
class VotePerformanceProfileConfigurationTest {

    /**
     * 验证基准模式关闭 P6Spy、全量 Trace 和高频框架 DEBUG。
     *
     * @throws IOException 配置资源读取失败
     */
    @Test
    @DisplayName("performance profile 固定低诊断开销")
    void performanceProfileDisablesDiagnosticOverhead() throws IOException {
        String yaml = read("application-performance.yml");

        assertThat(yaml)
                .contains("p6spy: false")
                .contains("sampling-rate: ${TRACE_SAMPLING_RATE:0.0}")
                .contains("root: INFO")
                .contains("org.springframework.web: WARN")
                .contains("org.springframework.security: WARN")
                .contains("reactor.netty: WARN")
                .contains("com.baomidou.mybatisplus: WARN");
    }

    /**
     * 读取 classpath 中的 UTF-8 配置资源。
     *
     * @param resourceName 资源文件名
     * @return 配置文件原文
     * @throws IOException 配置资源读取失败
     */
    private String read(String resourceName) throws IOException {
        return new ClassPathResource(resourceName).getContentAsString(StandardCharsets.UTF_8);
    }
}
