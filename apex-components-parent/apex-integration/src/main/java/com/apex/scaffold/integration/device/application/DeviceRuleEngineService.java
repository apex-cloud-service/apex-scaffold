package com.apex.scaffold.integration.device.application;

import com.apex.scaffold.domain.service.DomainEventPublisher;
import com.apex.scaffold.integration.device.domain.DeviceRule;
import com.apex.scaffold.integration.device.domain.RuleCondition;
import com.apex.scaffold.integration.device.domain.event.DeviceRuleTriggered;
import com.apex.scaffold.integration.device.domain.repository.DeviceRuleRepository;
import com.apex.scaffold.integration.device.domain.service.NotifyActionPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 轻量规则引擎（方案 M5 / 批次 E3）。
 *
 * <p>对单条遥测指标评估租户启用规则：结构化条件（{@link RuleCondition}，字段点分
 * 路径 + 白名单操作符 + 阈值，仅声明式比较，无脚本执行面）全部命中即执行动作：
 * <ul>
 *   <li>{@code NOTIFY}：经可选 {@link NotifyActionPort}（{@code ObjectProvider} 注入，
 *       端口缺失时记日志跳过；本任务只定义端口，实现接 D1 消息门面）；</li>
 *   <li>{@code WEBHOOK}：经 connector 子域出站门面（{@code ConnectorInvokeService}）
 *       按 action_config.connectorKey 调用 Webhook 连接器，幂等键 =
 *       {@code ruleId:telemetry 触发 UUID}；</li>
 *   <li>{@code EVENT}：经 {@code DomainEventPublisher} 发布 {@link DeviceRuleTriggered}。</li>
 * </ul>
 *
 * <p>条件解析失败或动作执行失败均只记录告警，不影响遥测受理主链路。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Service
public class DeviceRuleEngineService {

    private final DeviceRuleRepository ruleRepository;

    private final ObjectProvider<NotifyActionPort> notifyPort;

    private final ObjectProvider<com.apex.scaffold.integration.connector.application.ConnectorInvokeService>
            connectorInvokeService;

    private final DomainEventPublisher eventPublisher;

    private final ObjectMapper objectMapper;

    /**
     * 构造规则引擎。
     *
     * @param ruleRepository         规则仓储
     * @param notifyPort             通知端口（可选）
     * @param connectorInvokeService connector 出站门面（可选；WEBHOOK 动作需要）
     * @param eventPublisher         领域事件发布器
     * @param objectMapper           Jackson
     */
    public DeviceRuleEngineService(
            DeviceRuleRepository ruleRepository,
            ObjectProvider<NotifyActionPort> notifyPort,
            ObjectProvider<com.apex.scaffold.integration.connector.application.ConnectorInvokeService>
                    connectorInvokeService,
            DomainEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.notifyPort = notifyPort;
        this.connectorInvokeService = connectorInvokeService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * 对一条遥测指标评估租户启用规则并执行命中动作。
     *
     * @param tenantId   租户编码
     * @param deviceKey  设备标识
     * @param metricJson 遥测指标 JSON（对象）
     */
    public Mono<Void> evaluate(String tenantId, String deviceKey, String metricJson) {
        Map<String, Object> metric = parseMetric(metricJson);
        return ruleRepository
                .findEnabled(tenantId)
                .flatMapMany(Flux::fromIterable)
                .concatMap(rule -> evaluateRule(rule, deviceKey, metricJson, metric))
                .then();
    }

    /** 单规则评估：条件全命中即执行动作；失败仅告警。 */
    private Mono<Void> evaluateRule(DeviceRule rule, String deviceKey, String metricJson, Map<String, Object> metric) {
        return Mono.fromCallable(() -> RuleCondition.parse(objectMapper, rule.getConditionJson()))
                .onErrorResume(badCondition -> {
                    log.warn("规则条件解析失败（跳过）: tenant={}, rule={}", rule.getTenantId(), rule.getRuleName());
                    return Mono.empty();
                })
                .filter(conditions -> RuleCondition.matchesAll(conditions, metric))
                .flatMap(conditions -> executeAction(rule, deviceKey, metricJson))
                .onErrorResume(error -> {
                    log.warn("规则动作执行失败（跳过）: tenant={}, rule={}", rule.getTenantId(), rule.getRuleName(), error);
                    return Mono.empty();
                });
    }

    /** 按动作类型分发。 */
    private Mono<Void> executeAction(DeviceRule rule, String deviceKey, String metricJson) {
        return switch (rule.getActionType() == null ? "" : rule.getActionType()) {
            case DeviceRule.ACTION_NOTIFY -> doNotify(rule, deviceKey);
            case DeviceRule.ACTION_WEBHOOK -> doWebhook(rule, deviceKey, metricJson);
            case DeviceRule.ACTION_EVENT -> eventPublisher.publish(new DeviceRuleTriggered(
                    rule.getTenantId(), rule.getId(), rule.getRuleName(), deviceKey, metricJson));
            default -> {
                log.warn("未知规则动作类型（跳过）: rule={}, actionType={}", rule.getRuleName(), rule.getActionType());
                yield Mono.empty();
            }
        };
    }

    /** NOTIFY：可选端口，缺失时记日志跳过。 */
    private Mono<Void> doNotify(DeviceRule rule, String deviceKey) {
        NotifyActionPort port = notifyPort != null ? notifyPort.getIfAvailable() : null;
        if (port == null) {
            log.info("通知端口未装配，NOTIFY 动作跳过: tenant={}, rule={}", rule.getTenantId(), rule.getRuleName());
            return Mono.empty();
        }
        String content = "设备规则命中: device=" + deviceKey + ", rule=" + rule.getRuleName();
        return port.notify(rule.getTenantId(), rule.getRuleName(), deviceKey, content);
    }

    /** WEBHOOK：经 connector 出站门面按 connectorKey 调用（E2 出站审计沿用）。 */
    private Mono<Void> doWebhook(DeviceRule rule, String deviceKey, String metricJson) {
        var invokeService = connectorInvokeService != null ? connectorInvokeService.getIfAvailable() : null;
        if (invokeService == null) {
            log.warn("connector 出站门面未装配，WEBHOOK 动作跳过: rule={}", rule.getRuleName());
            return Mono.empty();
        }
        String connectorKey = actionConfigValue(rule, "connectorKey");
        if (connectorKey == null || connectorKey.isBlank()) {
            log.warn("WEBHOOK 动作缺少 connectorKey 配置: rule={}", rule.getRuleName());
            return Mono.empty();
        }
        Map<String, Object> payload = new HashMap<>(3);
        payload.put("rule", rule.getRuleName());
        payload.put("deviceKey", deviceKey);
        payload.put("metric", parseMetric(metricJson));
        return invokeService
                .execute(rule.getTenantId(), connectorKey, "iot.rule", payload, rule.getId() + ":" + UUID.randomUUID())
                .then();
    }

    /** 从动作配置 JSON 提取文本字段（解析失败返回 null）。 */
    private String actionConfigValue(DeviceRule rule, String field) {
        if (rule.getActionConfig() == null || rule.getActionConfig().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> config = objectMapper.readValue(rule.getActionConfig(), new TypeReference<>() {});
            Object value = config.get(field);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> parseMetric(String metricJson) {
        try {
            return objectMapper.readValue(metricJson, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
