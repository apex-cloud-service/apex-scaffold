package com.apex.scaffold.domain.model.moderation;

import java.util.Comparator;
import java.util.List;

/**
 * 文本审核聚合结果值对象。
 *
 * <p>聚合文本审核命中项，按 BLOCK &gt; REVIEW &gt; PASS 的优先级确定最终审核决定。</p>
 *
 * @param decision 最终审核决定
 * @param provider 审核服务提供商
 * @param requestId 供应商请求 ID（如百度 log_id）
 * @param findings 各命中项结果列表
 * @author weiqiang.ling
 * @version 1.0.0
 * @since 1.0.0
 */
public record TextModerationResult(
        ModerationDecision decision, String provider, String requestId, List<ModerationFinding> findings) {

    /**
     * 校验并复制审核结果列表。
     *
     * @param findings 审核结果列表
     */
    public TextModerationResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
        if (findings.isEmpty()) {
            throw new IllegalArgumentException("审核结果不能为空");
        }
    }

    /**
     * 按 BLOCK &gt; REVIEW &gt; PASS 聚合命中项结果。
     *
     * @param provider 审核服务提供商
     * @param requestId 供应商请求 ID
     * @param findings 各命中项结果列表
     * @return 聚合后的文本审核结果
     */
    public static TextModerationResult aggregate(String provider, String requestId, List<ModerationFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            throw new IllegalArgumentException("审核结果不能为空");
        }
        ModerationDecision decision = findings.stream()
                .map(ModerationFinding::decision)
                .max(Comparator.comparingInt(ModerationDecision::severity))
                .orElseThrow();
        return new TextModerationResult(decision, provider, requestId, findings);
    }
}
