package com.apex.scaffold.domain.model.generator;

/**
 * 代码生成器异步任务状态
 *
 * <p>状态机为单向推进：{@link #PENDING} → {@link #RUNNING} → {@link #COMPLETED} /
 * {@link #FAILED}，运行中允许被取消进入 {@link #CANCELED}。终态不再被轮询调度器
 * 认领，进度更新对终态任务幂等丢弃。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum GenTaskStatus {

    /** 已提交，等待调度实例认领。 */
    PENDING,

    /** 已被实例认领，正在逐个表与模板渲染发布。 */
    RUNNING,

    /** 全部产物发布完成。 */
    COMPLETED,

    /** 重试耗尽或渲染发布失败。 */
    FAILED,

    /** 运行中被取消，或提交后取消。 */
    CANCELED;

    /**
     * 判断是否为终态。
     *
     * <p>终态任务不能被调度器重新认领，也不会再推进进度。
     *
     * @return 终态返回 {@code true}
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED;
    }

    /**
     * 按持久化字符串解析状态。
     *
     * @param value 状态字符串（忽略大小写与首尾空白）
     * @return 对应状态；无法识别时返回 {@code null}，由调用方决定降级口径
     */
    public static GenTaskStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        for (GenTaskStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        return null;
    }
}
