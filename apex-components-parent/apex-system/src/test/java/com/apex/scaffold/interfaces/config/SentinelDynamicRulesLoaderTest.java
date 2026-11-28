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

    /** 静态基线快照所需的国际化工具替身（本用例不触及消息解析路径）。 */
    private final com.apex.scaffold.interfaces.util.MessageUtils messageUtils =
            org.mockito.Mockito.mock(com.apex.scaffold.interfaces.util.MessageUtils.class);

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
     * 验证真实默认种子文件与 {@code SentinelConfig} 静态基线一致（资源名 + 阈值）。
     *
     * <p>护栏来源（BE-072 N-3/N-5）：该种子经 {@code SentinelDynamicRulesLoader} 以整体替换
     * 语义加载，若与 {@code SentinelConfig} 静态基线漂移（BE-072 前即为退役短路径命名），
     * 显式注入 rules-file 的部署态会清掉静态规则。本用例不硬编码期望值，而是先由
     * {@code SentinelConfig} 实际初始化快照静态规则（资源名 + count），再加载种子逐值比对，
     * 从而能同时捕获「资源集漂移」与「阈值漂移」两类最可能发生的形态。</p>
     *
     * <p>注：种子文件位于仓库根 {@code config/sentinel/}（文件系统路径，非 classpath 资源），
     * 且默认打包产物中不含该文件，故 Loader 默认态跳过加载；本用例以显式路径加载以校验对齐性。</p>
     */
    @Test
    @DisplayName("默认种子文件应与 SentinelConfig 静态基线逐值一致")
    void defaultSeedFileShouldMatchStaticBaseline() throws Exception {
        // 1) 快照静态基线（资源名 → count）
        new SentinelConfig(true, 0.8, 2000, messageUtils).initFlowRules();
        var staticFlow = FlowRuleManager.getRules().stream()
                .collect(java.util.stream.Collectors.toMap(
                        rule -> rule.getResource(), rule -> rule.getCount(), (a, b) -> a));
        new SentinelConfig(true, 0.8, 2000, messageUtils).initDegradeRules();
        var staticDegrade = DegradeRuleManager.getRules().stream()
                .collect(java.util.stream.Collectors.toMap(
                        rule -> rule.getResource(), rule -> rule.getCount(), (a, b) -> a));

        // 2) 加载种子（整体替换后即为种子产物）
        String seedPath = java.nio.file.Path.of(
                        System.getProperty("user.dir"), "..", "..", "config", "sentinel", "sentinel-rules.json")
                .toAbsolutePath()
                .toString();
        newLoader(seedPath).loadRules();

        var seedFlow = FlowRuleManager.getRules().stream()
                .collect(java.util.stream.Collectors.toMap(
                        rule -> rule.getResource(), rule -> rule.getCount(), (a, b) -> a));
        var seedDegrade = DegradeRuleManager.getRules().stream()
                .collect(java.util.stream.Collectors.toMap(
                        rule -> rule.getResource(), rule -> rule.getCount(), (a, b) -> a));

        // 3) 逐值比对：资源集与阈值均须一致
        assertThat(seedFlow).isEqualTo(staticFlow);
        assertThat(seedDegrade).isEqualTo(staticDegrade);
    }

    /**
     * 使用指定规则文件路径创建加载器
     */
    private SentinelDynamicRulesLoader newLoader(String path) {
        return new SentinelDynamicRulesLoader(objectMapper, new DefaultResourceLoader(), path);
    }
}
