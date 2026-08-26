package com.apex.scaffold.domain.model.moderation;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个检测场景的命中结果值对象。
 *
 * <p>封装审核场景的检测结果，包括场景、建议、标签、置信度和扩展详情。</p>
 *
 * @param scene 审核场景编码
 * @param suggestion 供应商建议（pass/review/block）
 * @param label 命中的标签名称
 * @param rate 命中置信度（0.0~1.0）
 * @param details 扩展详情（不可变）
 * @author weiqiang.ling
 * @version 1.0.0
 * @since 1.0.0
 */
public record ModerationFinding(
        String scene, String suggestion, String label, BigDecimal rate, Map<String, Object> details) {

    /**
     * 创建审核命中结果（无扩展详情）。
     *
     * @param scene 审核场景编码
     * @param suggestion 供应商建议
     * @param label 命中的标签名称
     * @param rate 命中置信度
     */
    public ModerationFinding(String scene, String suggestion, String label, BigDecimal rate) {
        this(scene, suggestion, label, rate, Map.of());
    }

    /**
     * 校验并复制扩展详情。
     *
     * @param details 扩展详情
     */
    public ModerationFinding {
        details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    /**
     * 根据供应商建议返回对应的领域审核决定。
     *
     * @return 领域审核决定，由 {@link ModerationDecision#fromSuggestion(String)} 转换而来
     */
    public ModerationDecision decision() {
        return ModerationDecision.fromSuggestion(suggestion);
    }
}
