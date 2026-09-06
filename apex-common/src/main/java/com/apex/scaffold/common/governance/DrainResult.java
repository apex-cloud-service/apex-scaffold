package com.apex.scaffold.common.governance;

/**
 * 资源排空结果。
 *
 * @param participant 参与者名称
 * @param drained 是否排空完成
 * @param detail 有限诊断信息
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record DrainResult(String participant, boolean drained, String detail) {
    /**
     * 创建成功结果。
     *
     * @param participant 参与者名称
     * @return 排空结果
     */
    public static DrainResult drained(String participant) {
        return new DrainResult(participant, true, "drained");
    }

    /**
     * 创建超时结果。
     *
     * @param participant 参与者名称
     * @param detail 诊断信息
     * @return 排空结果
     */
    public static DrainResult timedOut(String participant, String detail) {
        return new DrainResult(participant, false, detail);
    }
}
