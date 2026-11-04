package com.apex.scaffold.integration.device.application;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.device.domain.DeviceRule;
import com.apex.scaffold.integration.device.domain.RuleCondition;
import com.apex.scaffold.integration.device.domain.repository.DeviceRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 轻量规则管理用例（方案 M5 / 批次 E3）。
 *
 * <p>创建时对 {@code conditionJson} 做结构化预检（{@link RuleCondition#parse}），
 * 保证入库规则均可被规则引擎解析；动作类型限定白名单。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceRuleManageService {

    private static final Set<String> ALLOWED_ACTIONS =
            Set.of(DeviceRule.ACTION_NOTIFY, DeviceRule.ACTION_WEBHOOK, DeviceRule.ACTION_EVENT);

    private final DeviceRuleRepository ruleRepository;

    private final ObjectMapper objectMapper;

    /**
     * 新增规则（启用状态按入参）。
     *
     * @param tenantId      租户编码
     * @param ruleName      规则名（租户内唯一）
     * @param conditionJson 结构化条件 JSON
     * @param actionType    动作类型（NOTIFY/WEBHOOK/EVENT）
     * @param actionConfig  动作配置 JSON（可空）
     * @param enabled       是否启用
     * @return 落库后的规则
     */
    public Mono<DeviceRule> create(
            String tenantId,
            String ruleName,
            String conditionJson,
            String actionType,
            String actionConfig,
            boolean enabled) {
        if (ruleName == null || ruleName.isBlank() || ruleName.length() > 64) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "规则名须为 1~64 位非空白文本"));
        }
        if (actionType == null || !ALLOWED_ACTIONS.contains(actionType)) {
            return Mono.error(
                    new BusinessException(ResultCode.PARAM_ERROR, "动作类型仅支持 NOTIFY/WEBHOOK/EVENT: " + actionType));
        }
        return Mono.fromCallable(() -> {
                    RuleCondition.parse(objectMapper, conditionJson);
                    return conditionJson;
                })
                .onErrorMap(
                        IllegalArgumentException.class,
                        e -> new BusinessException(ResultCode.PARAM_ERROR, e.getMessage()))
                .map(validCondition -> {
                    DeviceRule rule = new DeviceRule();
                    rule.setTenantId(tenantId);
                    rule.setRuleName(ruleName.trim());
                    rule.setConditionJson(validCondition);
                    rule.setActionType(actionType);
                    rule.setActionConfig(actionConfig);
                    rule.setEnabled(enabled ? 1 : 0);
                    return rule;
                })
                .flatMap(ruleRepository::insert);
    }

    /**
     * 规则启停（租户限定；0 行视为不存在）。
     *
     * @param tenantId 租户编码
     * @param id       规则主键
     * @param enabled  是否启用
     */
    public Mono<Void> toggle(String tenantId, Long id, boolean enabled) {
        return ruleRepository
                .updateEnabled(tenantId, id, enabled)
                .flatMap(updated ->
                        updated ? Mono.empty() : Mono.error(new BusinessException(ResultCode.NOT_FOUND, "规则不存在")));
    }

    /**
     * 规则详情。
     *
     * @param tenantId 租户编码
     * @param id       规则主键
     * @return 规则
     */
    public Mono<DeviceRule> get(String tenantId, Long id) {
        return ruleRepository
                .findById(tenantId, id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "规则不存在")));
    }

    /**
     * 规则分页列表（管理端，E3 补强）。
     *
     * @param tenantId 租户编码
     * @param keyword  规则名关键字（可空）
     * @param enabled  启停过滤（可空）
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public Mono<PageResponse<DeviceRule>> page(
            String tenantId, String keyword, Boolean enabled, int pageNum, int pageSize) {
        return ruleRepository.page(tenantId, keyword, enabled, pageNum, pageSize);
    }
}
