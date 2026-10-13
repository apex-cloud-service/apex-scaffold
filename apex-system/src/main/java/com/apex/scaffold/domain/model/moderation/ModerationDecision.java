package com.apex.scaffold.domain.model.moderation;

/**
 * 内容审核决定枚举。
 *
 * <p>严重程度依次递增，数值越大表示审核决定越严格。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum ModerationDecision {

    /** 通过（严重程度0） */
    PASS(0),

    /** 需人工复核（严重程度1） */
    REVIEW(1),

    /** 拦截（严重程度2） */
    BLOCK(2);

    /** 严重程度数值，数值越大越严重 */
    private final int severity;

    /**
     * 创建 {@link ModerationDecision} 实例。
     *
     * @param severity severity 参数
     */
    ModerationDecision(int severity) {
        this.severity = severity;
    }

    /**
     * 获取审核决定的严重程度数值。
     *
     * @return 严重程度数值，数值越大越严重
     */
    public int severity() {
        return severity;
    }

    /**
     * 将供应商 suggestion 严格转换为领域决定，未知值拒绝放行。
     *
     * @param suggestion 供应商建议，不区分大小写
     * @return 领域审核决定
     * @throws IllegalArgumentException 当 suggestion 为空或为未知值时
     */
    public static ModerationDecision fromSuggestion(String suggestion) {
        if (suggestion == null) {
            throw new IllegalArgumentException("审核建议不能为空");
        }
        return switch (suggestion.toLowerCase(java.util.Locale.ROOT)) {
            case "pass" -> PASS;
            case "review" -> REVIEW;
            case "block" -> BLOCK;
            default -> throw new IllegalArgumentException("未知审核建议: " + suggestion);
        };
    }
}
