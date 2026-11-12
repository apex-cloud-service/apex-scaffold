package com.apex.scaffold.office.domain.approval;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 审批撤销编排状态
 *
 * <p>描述一次撤销操作在"远端平台撤销"与"本地流程状态更新"两步之间的推进阶段，
 * 供幂等重放与失败补偿判定使用。
 *
 * @author Apex Team
 * @since 1.4.0
 */
@Getter
@RequiredArgsConstructor
public enum ApprovalCancelStatus {

    /** 已受理，尚未收到远端结果 */
    PENDING("待撤销"),

    /** 远端撤销成功，本地流程状态尚未落库（补偿待完成） */
    REMOTE_CANCELLED("远端已撤销待落库"),

    /** 远端与本地均已完成，重放直接返回成功 */
    COMPLETED("已完成"),

    /** 远端或本地失败，需重试 */
    FAILED("失败");

    private final String description;

    /**
     * 重放是否可以直接返回成功
     *
     * <p>远端已撤销的阶段同样视为成功：再次调用不应重复触发远端撤销。
     *
     * @return 已完成或远端已撤销返回 true
     */
    public boolean isReplayableAsSuccess() {
        return this == COMPLETED || this == REMOTE_CANCELLED;
    }
}
