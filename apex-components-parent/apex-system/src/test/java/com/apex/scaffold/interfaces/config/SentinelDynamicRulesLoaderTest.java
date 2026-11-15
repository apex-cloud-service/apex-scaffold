package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Sentinel 动态规则加载器测试
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@DisplayName("Sentinel 动态规则加载测试")
class SentinelDynamicRulesLoaderTest {

    private SentinelDynamicRulesLoader loader;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        loader = new SentinelDynamicRulesLoader(
                objectMapper, new DefaultResourceLoader(), "classpath:config/sentinel/sentinel-rules.json");
    }

    @Test
    @DisplayName("应成功加载完整的规则配置文件")
    void shouldLoadCompleteRulesFile() throws IOException {
        // Given
        String jsonContent =
                """
                {
                  "flowRules": [
                    {
                      "resource": "/test/api",
                      "grade": 1,
                      "count": 100,
                      "strategy": 0,
                      "controlBehavior": 0,
                      "clusterMode": false
                    }
                  ],
                  "degradeRules": [
                    {
                      "resource": "/test/api",
                      "grade": 0,
                      "count": 1000,
                      "timeWindow": 10,
                      "minRequestAmount": 5,
                      "statIntervalMs": 30000,
                      "slowRatioThreshold": 0.5
                    }
                  ],
                  "systemRules": [
                    {
                      "highestSystemLoad": -1,
                      "avgRt": -1,
                      "maxThread": -1,
                      "qps": 5000,
                      "highestCpuUsage": 0.85
                    }
                  ]
                }
                """;

        Path rulesFile = tempDir.resolve("test-rules.json");
        Files.writeString(rulesFile, jsonContent);

        // 通过构造器设置文件路径
        loader = newLoader("file:" + rulesFile.toAbsolutePath());

        // When
        loader.loadRules();

        // Then
        assertThat(FlowRuleManager.getRules()).hasSize(1);
        assertThat(DegradeRuleManager.getRules()).hasSize(1);
        // SystemRuleManager 会按启用属性重建规则：fixture 启用 highestCpuUsage 与 qps
        // 两个属性（其余为 -1 未启用），因此 getRules() 返回 2 条 SystemRule
        assertThat(SystemRuleManager.getRules()).hasSize(2);
    }

    @Test
    @DisplayName("文件不存在时应优雅降级，不影响启动")
    void shouldGracefullyHandleMissingFile() {
        // Given
        loader = newLoader("file:/nonexistent/path/rules.json");

        // When & Then: 不应抛出异常
        loader.loadRules();
    }

    @Test
    @DisplayName("应正确解析流控规则的所有字段")
    void shouldParseAllFlowRuleFields() throws IOException {
        // Given
        String jsonContent =
                """
                {
                  "flowRules": [
                    {
                      "resource": "/auth/login",
                      "grade": 1,
                      "count": 200,
                      "strategy": 0,
                      "controlBehavior": 0,
                      "clusterMode": false
                    }
                  ]
                }
                """;

        Path rulesFile = tempDir.resolve("flow-rules.json");
        Files.writeString(rulesFile, jsonContent);
        loader = newLoader("file:" + rulesFile.toAbsolutePath());

        // When
        loader.loadRules();

        // Then
        var rules = FlowRuleManager.getRules();
        assertThat(rules).hasSize(1);
        var rule = rules.get(0);
        assertThat(rule.getResource()).isEqualTo("/auth/login");
        assertThat(rule.getGrade()).isEqualTo(1);
        assertThat(rule.getCount()).isEqualTo(200.0);
    }

    /**
     * 使用指定规则文件路径创建加载器
     */
    private SentinelDynamicRulesLoader newLoader(String path) {
        return new SentinelDynamicRulesLoader(objectMapper, new DefaultResourceLoader(), path);
    }
}
