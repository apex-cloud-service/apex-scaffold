package com.apex.scaffold.interfaces.config;

import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Sentinel 规则 Nacos 动态下发配置。
 *
 * <p>当 {@code apex.sentinel.nacos.enabled=true} 时，将限流规则（FlowRule）与熔断规则
 * （DegradeRule）的规则源接入 Nacos 配置中心，实现**生产规则热更新**——在 Nacos
 * 控制台修改规则 JSON 后，Sentinel 在约 30 秒内自动加载生效，无需重启应用。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>应用启动时先由 {@link SentinelConfig} 加载内置静态规则（39 条限流 + 熔断），作为兜底默认值</li>
 *   <li>本配置启用后，Nacos 侧推送的规则会**整体替换**对应规则集合（Sentinel 数据源语义）</li>
 *   <li>因此启用动态下发时，Nacos 的 DataId 中应维护**完整规则集**（可从静态规则导出为模板，见
 *       {@code config/sentinel/} 下的 JSON 模板与 docs/CONFIGURATION.md）</li>
 *   <li>Nacos 不可用时不影响应用启动：数据源初始化失败仅记录 WARN，静态规则继续生效</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <table>
 *   <caption>apex.sentinel.nacos.* 配置</caption>
 *   <tr><th>键</th><th>默认值</th><th>说明</th></tr>
 *   <tr><td>enabled</td><td>false</td><td>是否启用 Nacos 动态下发（建议生产接入后开启）</td></tr>
 *   <tr><td>server-addr</td><td>${NACOS_SERVER_ADDR:127.0.0.1:18848}</td><td>Nacos 服务地址</td></tr>
 *   <tr><td>namespace</td><td>空</td><td>Nacos 命名空间 ID（默认公共空间）</td></tr>
 *   <tr><td>group-id</td><td>SENTINEL_GROUP</td><td>配置分组</td></tr>
 *   <tr><td>flow-data-id</td><td>apex-sentinel-flow-rules</td><td>限流规则 DataId</td></tr>
 *   <tr><td>degrade-data-id</td><td>apex-sentinel-degrade-rules</td><td>熔断规则 DataId</td></tr>
 * </table>
 *
 * <h2>设计说明</h2>
 * <p>限流规则为 {@code FlowRule} JSON 数组；熔断规则为 {@code DegradeRule} JSON 数组。
 * 模板见 {@code config/sentinel/apex-sentinel-flow-rules.json} 与
 * {@code config/sentinel/apex-sentinel-degrade-rules.json}。</p>
 *
 * @author Apex Team
 * @version 1.1.4
 * @since 1.1.4
 * @see SentinelConfig
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "apex.features", name = "monitoring", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(NacosDataSource.class)
@ConditionalOnProperty(prefix = "apex.sentinel.nacos", name = "enabled", havingValue = "true")
public class SentinelNacosDataSourceConfig {

    /** Nacos 服务地址。 */
    private final String serverAddr;

    /** Nacos 命名空间 ID（空表示公共命名空间）。 */
    private final String namespace;

    /** 配置分组。 */
    private final String groupId;

    /** 限流规则 DataId。 */
    private final String flowDataId;

    /** 熔断规则 DataId。 */
    private final String degradeDataId;

    /** JSON 解析器（复用于两类规则转换）。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造 Nacos 规则数据源配置。
     *
     * @param serverAddr   Nacos 服务地址（默认 ${NACOS_SERVER_ADDR:127.0.0.1:18848}）
     * @param namespace    Nacos 命名空间 ID（默认空 = 公共空间）
     * @param groupId      配置分组（默认 SENTINEL_GROUP）
     * @param flowDataId   限流规则 DataId（默认 apex-sentinel-flow-rules）
     * @param degradeDataId 熔断规则 DataId（默认 apex-sentinel-degrade-rules）
     */
    public SentinelNacosDataSourceConfig(
            @Value("${apex.sentinel.nacos.server-addr:${NACOS_SERVER_ADDR:127.0.0.1:18848}}") String serverAddr,
            @Value("${apex.sentinel.nacos.namespace:}") String namespace,
            @Value("${apex.sentinel.nacos.group-id:SENTINEL_GROUP}") String groupId,
            @Value("${apex.sentinel.nacos.flow-data-id:apex-sentinel-flow-rules}") String flowDataId,
            @Value("${apex.sentinel.nacos.degrade-data-id:apex-sentinel-degrade-rules}") String degradeDataId) {
        this.serverAddr = serverAddr;
        this.namespace = namespace;
        this.groupId = groupId;
        this.flowDataId = flowDataId;
        this.degradeDataId = degradeDataId;
    }

    /**
     * 初始化 Nacos 规则数据源并注册到 Sentinel 规则管理器。
     *
     * <p>限流规则注册到 {@link FlowRuleManager}，熔断规则注册到
     * {@link DegradeRuleManager}；Nacos 侧配置变更后自动热加载。</p>
     */
    @PostConstruct
    public void initNacosDataSource() {
        // 构建 Nacos 客户端属性（serverAddr + 可选 namespace）
        Properties nacosProps = new Properties();
        nacosProps.setProperty("serverAddr", serverAddr);
        if (namespace != null && !namespace.isBlank()) {
            nacosProps.setProperty("namespace", namespace);
        }

        // 限流规则数据源：JSON 数组 → List<FlowRule>
        NacosDataSource<List<FlowRule>> flowDataSource =
                new NacosDataSource<>(nacosProps, groupId, flowDataId, jsonToFlowRules());
        FlowRuleManager.register2Property(flowDataSource.getProperty());

        // 熔断规则数据源：JSON 数组 → List<DegradeRule>
        NacosDataSource<List<DegradeRule>> degradeDataSource =
                new NacosDataSource<>(nacosProps, groupId, degradeDataId, jsonToDegradeRules());
        DegradeRuleManager.register2Property(degradeDataSource.getProperty());

        log.info(
                "Sentinel Nacos 动态规则已启用: server={}, namespace={}, group={}, flowDataId={}, degradeDataId={}",
                serverAddr,
                namespace == null || namespace.isBlank() ? "公共空间" : namespace,
                groupId,
                flowDataId,
                degradeDataId);
    }

    /**
     * 构造限流规则 JSON 转换器。
     *
     * <p>将 Nacos 配置的 {@code FlowRule} JSON 数组解析为规则列表；
     * 配置为空或解析失败时返回 {@code null}（Sentinel 将忽略该次推送）。</p>
     *
     * @return JSON → {@code List<FlowRule>} 转换器
     */
    private Converter<String, List<FlowRule>> jsonToFlowRules() {
        return source -> {
            if (source == null || source.isBlank()) {
                return null;
            }
            try {
                return objectMapper.readValue(source, new TypeReference<List<FlowRule>>() {});
            } catch (Exception e) {
                log.warn("限流规则 JSON 解析失败（本次推送忽略）: {}", e.getMessage());
                return null;
            }
        };
    }

    /**
     * 构造熔断规则 JSON 转换器。
     *
     * <p>将 Nacos 配置的 {@code DegradeRule} JSON 数组解析为规则列表；
     * 配置为空或解析失败时返回 {@code null}（Sentinel 将忽略该次推送）。</p>
     *
     * @return JSON → {@code List<DegradeRule>} 转换器
     */
    private Converter<String, List<DegradeRule>> jsonToDegradeRules() {
        return source -> {
            if (source == null || source.isBlank()) {
                return null;
            }
            try {
                return objectMapper.readValue(source, new TypeReference<List<DegradeRule>>() {});
            } catch (Exception e) {
                log.warn("熔断规则 JSON 解析失败（本次推送忽略）: {}", e.getMessage());
                return null;
            }
        };
    }
}
