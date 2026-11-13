package com.apex.scaffold.office.domain.approval;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批撤销操作编排状态聚合。
 *
 * <p>记录一次撤销请求的幂等键与推进阶段，用于：
 * <ul>
 *   <li>重复请求重放：命中已完成或远端已撤销的记录直接返回成功，不再触发远端撤销</li>
 *   <li>失败补偿：远端成功而本地落库失败时停留在"远端已撤销"阶段，可重试补齐本地状态</li>
 *   <li>审计：保留失败原因与时间线</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalCancelOperation {

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 业务流程实例ID */
    private String processInstanceId;

    /** 幂等键（租户内唯一） */
    private String idempotencyKey;

    /** 编排状态 */
    private ApprovalCancelStatus status;

    /** 最近一次失败原因 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 构造一个待撤销的编排记录。
     *
     * @param tenantId 租户ID
     * @param processInstanceId 业务流程实例ID
     * @param idempotencyKey 幂等键
     * @return 处于 PENDING 阶段的编排记录
     */
    public static ApprovalCancelOperation pending(Long tenantId, String processInstanceId, String idempotencyKey) {
        return ApprovalCancelOperation.builder()
                .tenantId(tenantId)
                .processInstanceId(processInstanceId)
                .idempotencyKey(idempotencyKey)
                .status(ApprovalCancelStatus.PENDING)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
    }

    /**
     * 重置为待撤销阶段，用于失败后重试。
     *
     * <p>保持幂等键不变，仅刷新时间并清空上次失败原因。
     */
    public void markPending() {
        this.status = ApprovalCancelStatus.PENDING;
        this.errorMessage = null;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 标记远端撤销成功、本地尚未落库。
     */
    public void markRemoteCancelled() {
        this.status = ApprovalCancelStatus.REMOTE_CANCELLED;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 标记远端与本地均已完成。
     */
    public void markCompleted() {
        this.status = ApprovalCancelStatus.COMPLETED;
        this.errorMessage = null;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 标记失败并保留原因。
     *
     * @param errorMessage 失败原因
     */
    public void markFailed(String errorMessage) {
        this.status = ApprovalCancelStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 重放是否可直接返回成功。
     *
     * @return 已完成或远端已撤销返回 true
     */
    public boolean isReplayableAsSuccess() {
        return status != null && status.isReplayableAsSuccess();
    }
}
