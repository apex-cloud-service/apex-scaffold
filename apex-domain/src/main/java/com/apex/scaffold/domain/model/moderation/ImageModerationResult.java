package com.apex.scaffold.domain.model.moderation;

import java.util.Comparator;
import java.util.List;

/**
 * 图片审核聚合结果值对象。
 *
 * <p>聚合多个审核场景的检测结果，按BLOCK > REVIEW > PASS的优先级确定最终审核决定。</p>
 *
 * @param decision 最终审核决定
 * @param provider 审核服务提供商
 * @param requestId 请求ID
 * @param taskId 任务ID
 * @param findings 各场景命中结果列表
 * @author weiqiang.ling
 * @version 1.0.0
 * @since 1.0.0
 */
public record ImageModerationResult(
        ModerationDecision decision,
        String provider,
        String requestId,
        String taskId,
        List<ModerationFinding> findings) {

    /**
     * 校验并复制审核结果列表。
     *
     * @param findings 审核结果列表
     */
    public ImageModerationResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
        if (findings.isEmpty()) {
            throw new IllegalArgumentException("审核结果不能为空");
        }
    }

    /**
     * 按 BLOCK > REVIEW > PASS 聚合场景结果。
     *
     * @param provider 审核服务提供商
     * @param requestId 请求ID
     * @param taskId 任务ID
     * @param findings 各场景命中结果列表
     * @return 聚合后的图片审核结果
     */
    public static ImageModerationResult aggregate(
            String provider, String requestId, String taskId, List<ModerationFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            throw new IllegalArgumentException("审核结果不能为空");
        }
        ModerationDecision decision = findings.stream()
                .map(ModerationFinding::decision)
                .max(Comparator.comparingInt(ModerationDecision::severity))
                .orElseThrow();
        return new ImageModerationResult(decision, provider, requestId, taskId, findings);
    }
}
