package com.apex.scaffold.interfaces.config;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Sentinel 动态规则加载器
 *
 * <p>从外部 JSON 文件加载 Sentinel 流控、降级和系统保护规则，替代硬编码在 Java 代码中的配置方式。
 * 支持本地文件和 classpath 资源，便于运维人员在不重启应用的情况下动态调整规则（配合文件监听）。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>JSON 配置：规则以 JSON 格式存储，可读性强，易于版本管理</li>
 *   <li>多规则类型：支持流控规则（flowRules）、降级规则（degradeRules）、系统规则（systemRules）</li>
 *   <li>优雅降级：文件不存在或解析失败时记录警告，不影响应用启动</li>
 *   <li>灵活路径：支持绝对路径、相对路径和 classpath 资源</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <pre>{@code
 * {
 *   "flowRules": [
 *     {
 *       "resource": "/auth/login",
 *       "grade": 1,
 *       "count": 200,
 *       "strategy": 0,
 *       "controlBehavior": 0,
 *       "clusterMode": false
 *     }
 *   ],
 *   "degradeRules": [
 *     {
 *       "resource": "/auth/login",
 *       "grade": 0,
 *       "count": 1000,
 *       "timeWindow": 10,
 *       "minRequestAmount": 5,
 *       "statIntervalMs": 30000,
 *       "slowRatioThreshold": 0.5
 *     }
 *   ],
 *   "systemRules": [
 *     {
 *       "highestSystemLoad": -1,
 *       "avgRt": -1,
 *       "maxThread": -1,
 *       "qps": 5000,
 *       "highestCpuUsage": 0.85
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code apex.sentinel.rules-file}：规则文件路径，默认 classpath:config/sentinel/sentinel-rules.json</li>
 *   <li>{@code apex.sentinel.dynamic-rules.enabled}：是否启用动态加载，默认 true</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <table>
 *   <caption>FlowRule 字段</caption>
 *   <tr><th>字段</th><th>说明</th><th>取值</th></tr>
 *   <tr><td>resource</td><td>资源名（接口路径）</td><td>如 "/auth/login"</td></tr>
 *   <tr><td>grade</td><td>限流阈值类型</td><td>0-线程数, 1-QPS</td></tr>
 *   <tr><td>count</td><td>阈值</td><td>如 200 表示 200 QPS</td></tr>
 *   <tr><td>strategy</td><td>流控模式</td><td>0-直接, 1-关联, 2-链路</td></tr>
 *   <tr><td>controlBehavior</td><td>流控效果</td><td>0-快速失败, 1-Warm Up, 2-排队等待</td></tr>
 * </table>
 *
 * <table>
 *   <caption>DegradeRule 字段</caption>
 *   <tr><th>字段</th><th>说明</th><th>取值</th></tr>
 *   <tr><td>resource</td><td>资源名</td><td>接口路径</td></tr>
 *   <tr><td>grade</td><td>熔断策略</td><td>0-慢调用比例, 1-异常比例, 2-异常数</td></tr>
 *   <tr><td>count</td><td>阈值</td><td>慢调用 RT（ms）或异常比例</td></tr>
 *   <tr><td>timeWindow</td><td>熔断时长（秒）</td><td>熔断后多久进入半开状态</td></tr>
 *   <tr><td>minRequestAmount</td><td>最小请求数</td><td>触发熔断的最小请求量</td></tr>
 *   <tr><td>statIntervalMs</td><td>统计窗口（ms）</td><td>默认 30000（30秒）</td></tr>
 *   <tr><td>slowRatioThreshold</td><td>慢调用比例阈值</td><td>0.0-1.0，如 0.5 表示 50%</td></tr>
 * </table>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 * @see FlowRuleManager
 * @see DegradeRuleManager
 * @see SystemRuleManager
 */
@Slf4j
@Component
@ConditionalOnClass(FlowRuleManager.class)
@ConditionalOnProperty(
        prefix = "apex.sentinel.dynamic-rules",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SentinelDynamicRulesLoader {

    private static final String RULES_KEY_FLOW = "flowRules";
    private static final String RULES_KEY_DEGRADE = "degradeRules";
    private static final String RULES_KEY_SYSTEM = "systemRules";

    /** 规则文件路径，默认 {@code classpath:config/sentinel/sentinel-rules.json}。 */
    private final String rulesFilePath;

    /**
     * 获取规则文件路径（供文件监听器使用）
     *
     * @return 规则文件路径
     */
    public String getRulesFile() {
        return rulesFilePath;
    }

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    /**
     * 创建 Sentinel 动态规则加载器。
     *
     * @param objectMapper JSON 序列化工具
     * @param resourceLoader Spring 资源加载器
     * @param rulesFilePath 规则文件路径，默认 {@code classpath:config/sentinel/sentinel-rules.json}
     */
    public SentinelDynamicRulesLoader(
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            @Value("${apex.sentinel.rules-file:classpath:config/sentinel/sentinel-rules.json}") String rulesFilePath) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.rulesFilePath = rulesFilePath;
    }

    /**
     * 应用启动后加载规则
     */
    @PostConstruct
    public void loadRules() {
        try {
            log.info("开始从文件加载 Sentinel 规则: {}", rulesFilePath);

            InputStream inputStream = getInputStream();
            if (inputStream == null) {
                log.warn("Sentinel 规则文件不存在，跳过动态加载: {}", rulesFilePath);
                return;
            }

            JsonNode root = objectMapper.readTree(inputStream);

            if (root.has(RULES_KEY_FLOW)) {
                List<FlowRule> flowRules = parseFlowRules(root.get(RULES_KEY_FLOW));
                FlowRuleManager.loadRules(flowRules);
                log.info("加载 {} 条流控规则", flowRules.size());
            }

            if (root.has(RULES_KEY_DEGRADE)) {
                List<DegradeRule> degradeRules = parseDegradeRules(root.get(RULES_KEY_DEGRADE));
                DegradeRuleManager.loadRules(degradeRules);
                log.info("加载 {} 条降级规则", degradeRules.size());
            }

            if (root.has(RULES_KEY_SYSTEM)) {
                List<SystemRule> systemRules = parseSystemRules(root.get(RULES_KEY_SYSTEM));
                SystemRuleManager.loadRules(systemRules);
                log.info("加载 {} 条系统规则", systemRules.size());
            }

            log.info("Sentinel 规则加载完成");
        } catch (Exception e) {
            log.error("加载 Sentinel 规则失败: {}", rulesFilePath, e);
        }
    }

    /**
     * 从指定文件加载 Sentinel 规则
     *
     * <p>供规则文件监听器（{@link SentinelRuleFileWatcher}）在检测到文件变更时调用，
     * 支持热更新流控、降级和系统三类规则。加载语义与 {@link #loadRules()} 一致：
     * 先解析 JSON，再按规则类型分别应用到对应的 Sentinel RuleManager。
     *
     * <p>失败安全：任何异常都会被捕获并记录日志，原有规则保持不变，
     * 不会因规则文件内容非法导致服务中断。
     *
     * @param filePath 规则文件路径，支持 Spring Resource 前缀（classpath:、file:）或普通文件路径
     */
    public void loadRulesFromFile(String filePath) {
        try {
            log.info("开始从指定文件加载 Sentinel 规则: {}", filePath);

            InputStream inputStream = openStream(filePath);
            if (inputStream == null) {
                log.warn("指定的 Sentinel 规则文件不存在，跳过加载: {}", filePath);
                return;
            }

            try (inputStream) {
                JsonNode root = objectMapper.readTree(inputStream);
                applyRules(root);
            }

            log.info("Sentinel 规则从指定文件加载完成: {}", filePath);
        } catch (Exception e) {
            log.error("从指定文件加载 Sentinel 规则失败: {}", filePath, e);
        }
    }

    /**
     * 按规则类型应用解析后的规则
     *
     * @param root 规则文件 JSON 根节点
     */
    private void applyRules(JsonNode root) {
        if (root.has(RULES_KEY_FLOW)) {
            List<FlowRule> flowRules = parseFlowRules(root.get(RULES_KEY_FLOW));
            FlowRuleManager.loadRules(flowRules);
            log.info("加载 {} 条流控规则", flowRules.size());
        }

        if (root.has(RULES_KEY_DEGRADE)) {
            List<DegradeRule> degradeRules = parseDegradeRules(root.get(RULES_KEY_DEGRADE));
            DegradeRuleManager.loadRules(degradeRules);
            log.info("加载 {} 条降级规则", degradeRules.size());
        }

        if (root.has(RULES_KEY_SYSTEM)) {
            List<SystemRule> systemRules = parseSystemRules(root.get(RULES_KEY_SYSTEM));
            SystemRuleManager.loadRules(systemRules);
            log.info("加载 {} 条系统规则", systemRules.size());
        }
    }

    /**
     * 打开指定路径的规则文件输入流
     *
     * @param path 规则文件路径，优先按 Spring Resource 加载，失败后回退普通文件
     * @return 输入流；文件不存在时返回 {@code null}
     * @throws IOException 读取失败时抛出
     */
    private InputStream openStream(String path) throws IOException {
        // 尝试作为 Spring Resource 加载（支持 classpath:, file: 等）
        try {
            Resource resource = resourceLoader.getResource(path);
            if (resource.exists()) {
                return resource.getInputStream();
            }
        } catch (Exception e) {
            log.debug("作为 Spring Resource 加载失败，尝试作为文件路径: {}", path);
        }

        // 尝试作为普通文件加载
        File file = new File(path);
        if (file.exists() && file.isFile()) {
            return java.nio.file.Files.newInputStream(file.toPath());
        }

        return null;
    }

    /**
     * 获取输入流
     */
    private InputStream getInputStream() throws IOException {
        // 尝试作为 Spring Resource 加载（支持 classpath:, file: 等）
        try {
            Resource resource = resourceLoader.getResource(rulesFilePath);
            if (resource.exists()) {
                return resource.getInputStream();
            }
        } catch (Exception e) {
            log.debug("作为 Spring Resource 加载失败，尝试作为文件路径");
        }

        // 尝试作为普通文件加载
        File file = new File(rulesFilePath);
        if (file.exists() && file.isFile()) {
            return java.nio.file.Files.newInputStream(file.toPath());
        }

        return null;
    }

    /**
     * 解析流控规则
     */
    private List<FlowRule> parseFlowRules(JsonNode rulesNode) {
        List<FlowRule> rules = new ArrayList<>();
        for (JsonNode node : rulesNode) {
            FlowRule rule = new FlowRule();
            rule.setResource(node.get("resource").asText());
            rule.setGrade(node.get("grade").asInt());
            rule.setCount(node.get("count").asDouble());

            if (node.has("strategy")) {
                rule.setStrategy(node.get("strategy").asInt());
            }
            if (node.has("controlBehavior")) {
                rule.setControlBehavior(node.get("controlBehavior").asInt());
            }
            if (node.has("clusterMode")) {
                rule.setClusterMode(node.get("clusterMode").asBoolean());
            }

            rules.add(rule);
        }
        return rules;
    }

    /**
     * 解析降级规则
     */
    private List<DegradeRule> parseDegradeRules(JsonNode rulesNode) {
        List<DegradeRule> rules = new ArrayList<>();
        for (JsonNode node : rulesNode) {
            DegradeRule rule = new DegradeRule();
            rule.setResource(node.get("resource").asText());
            rule.setGrade(node.get("grade").asInt());
            rule.setCount(node.get("count").asDouble());
            rule.setTimeWindow(node.get("timeWindow").asInt());

            if (node.has("minRequestAmount")) {
                rule.setMinRequestAmount(node.get("minRequestAmount").asInt());
            }
            if (node.has("statIntervalMs")) {
                rule.setStatIntervalMs(node.get("statIntervalMs").asInt());
            }
            if (node.has("slowRatioThreshold")) {
                rule.setSlowRatioThreshold(node.get("slowRatioThreshold").asDouble());
            }

            rules.add(rule);
        }
        return rules;
    }

    /**
     * 解析系统规则
     */
    private List<SystemRule> parseSystemRules(JsonNode rulesNode) {
        List<SystemRule> rules = new ArrayList<>();
        for (JsonNode node : rulesNode) {
            SystemRule rule = new SystemRule();

            if (node.has("highestSystemLoad")) {
                rule.setHighestSystemLoad(node.get("highestSystemLoad").asDouble());
            }
            if (node.has("avgRt")) {
                rule.setAvgRt(node.get("avgRt").asLong());
            }
            if (node.has("maxThread")) {
                rule.setMaxThread(node.get("maxThread").asLong());
            }
            if (node.has("qps")) {
                rule.setQps(node.get("qps").asDouble());
            }
            if (node.has("highestCpuUsage")) {
                rule.setHighestCpuUsage(node.get("highestCpuUsage").asDouble());
            }

            rules.add(rule);
        }
        return rules;
    }
}
