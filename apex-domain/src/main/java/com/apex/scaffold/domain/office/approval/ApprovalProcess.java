package com.apex.scaffold.domain.office.approval;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批流程聚合根
 *
 * <p>表示一个完整的审批流程实例，包含审批的基本信息、当前状态、审批记录等。
 * 作为聚合根，负责维护审批流程的完整性和一致性，所有对审批流程的操作都需要通过此聚合根进行。
 *
 * <p>审批流程的生命周期：
 * <ul>
 *   <li>PENDING: 待审批，刚创建或等待下一个审批人处理</li>
 *   <li>IN_PROGRESS: 审批中，正在被某个审批人处理</li>
 *   <li>APPROVED: 已通过，所有审批节点都已同意</li>
 *   <li>REJECTED: 已拒绝，被某个审批人拒绝</li>
 *   <li>CANCELLED: 已撤销，被发起人撤回</li>
 *   <li>TRANSFERRED: 已转交，被当前审批人转交给其他人</li>
 * </ul>
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalProcess {

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 办公平台类型（feishu/dingtalk/wecom） */
    private String platform;

    /** 平台中的审批实例ID */
    private String platformInstanceId;

    /** 业务流程实例ID（业务系统中的流程ID） */
    private String processInstanceId;

    /** 审批模板ID */
    private String templateId;

    /** 审批标题 */
    private String title;

    /** 发起人ID */
    private String initiatorId;

    /** 发起人姓名 */
    private String initiatorName;

    /** 审批状态 */
    private ApprovalStatus status;

    /** 审批表单数据（JSON格式） */
    private String formData;

    /** 审批记录列表 */
    private List<ApprovalRecord> records;

    /** 当前审批人ID列表（JSON格式） */
    private String currentApproverIds;

    /** 审批发起时间 */
    private LocalDateTime initiateTime;

    /** 审批完成时间 */
    private LocalDateTime completeTime;

    /** 乐观锁版本号 */
    private Integer version;

    /** 逻辑删除标记 */
    private Boolean deleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 检查审批流程是否已完成
     *
     * @return 如果审批已通过、拒绝或撤销，返回true；否则返回false
     */
    public boolean isCompleted() {
        return status == ApprovalStatus.APPROVED
                || status == ApprovalStatus.REJECTED
                || status == ApprovalStatus.CANCELLED;
    }

    /**
     * 检查审批流程是否可以被撤销
     *
     * @return 如果审批处于待审批或审批中状态，返回true；否则返回false
     */
    public boolean isCancellable() {
        return status == ApprovalStatus.PENDING || status == ApprovalStatus.IN_PROGRESS;
    }

    /**
     * 更新审批状态
     *
     * @param newStatus 新的审批状态
     */
    public void updateStatus(ApprovalStatus newStatus) {
        this.status = newStatus;
        if (isCompleted()) {
            this.completeTime = LocalDateTime.now();
        }
    }

    /**
     * 添加审批记录
     *
     * @param record 审批记录
     */
    public void addRecord(ApprovalRecord record) {
        if (this.records == null) {
            this.records = new java.util.ArrayList<>();
        }
        this.records.add(record);
    }
}
