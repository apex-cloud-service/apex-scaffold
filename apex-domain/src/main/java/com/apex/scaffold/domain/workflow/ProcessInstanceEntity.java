package com.apex.scaffold.domain.workflow;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 流程实例实体。
 *
 * <p>表示一个正在运行或已完成的工作流实例。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public class ProcessInstanceEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private Long definitionId;
    private String processCode;
    private String title;
    private ProcessStatus status;
    private Long initiatorId;
    private String initiatorName;
    private String businessKey;
    private String formData;
    private String currentNodeId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    protected ProcessInstanceEntity() {
        super();
    }

    /**
     * 创建流程实例实体
     *
     * @param tenantId 租户 ID
     * @param definitionId 关联的工作流定义 ID
     * @param processCode 流程编码
     * @param title 流程标题
     * @param initiatorId 发起人用户 ID
     * @param initiatorName 发起人姓名
     * @param businessKey 业务主键（关联业务系统的单据标识）
     * @param formData 业务表单数据（JSON 格式）
     * @return 初始状态为草稿（DRAFT）且已初始化时间戳的流程实例实体
     */
    public static ProcessInstanceEntity create(
            Long tenantId,
            Long definitionId,
            String processCode,
            String title,
            Long initiatorId,
            String initiatorName,
            String businessKey,
            String formData) {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.tenantId = tenantId;
        instance.definitionId = definitionId;
        instance.processCode = processCode;
        instance.title = title;
        instance.status = ProcessStatus.DRAFT;
        instance.initiatorId = initiatorId;
        instance.initiatorName = initiatorName;
        instance.businessKey = businessKey;
        instance.formData = formData;
        instance.initTimestamps();
        return instance;
    }

    /**
     * 启动流程实例，状态置为运行中并记录开始时间
     *
     * @param startNodeId 起始节点 ID
     */
    public void start(String startNodeId) {
        this.status = ProcessStatus.RUNNING;
        this.currentNodeId = startNodeId;
        this.startTime = LocalDateTime.now();
        touch();
    }

    /**
     * 将流程流转到指定节点
     *
     * @param nodeId 目标节点 ID
     */
    public void moveToNode(String nodeId) {
        this.currentNodeId = nodeId;
        touch();
    }

    /**
     * 流程审批通过，记录结束时间
     */
    public void approve() {
        this.status = ProcessStatus.APPROVED;
        this.endTime = LocalDateTime.now();
        touch();
    }

    /**
     * 流程审批拒绝，记录结束时间
     */
    public void reject() {
        this.status = ProcessStatus.REJECTED;
        this.endTime = LocalDateTime.now();
        touch();
    }

    /**
     * 取消流程，记录结束时间
     */
    public void cancel() {
        this.status = ProcessStatus.CANCELLED;
        this.endTime = LocalDateTime.now();
        touch();
    }

    /**
     * 终止流程，记录结束时间
     */
    public void terminate() {
        this.status = ProcessStatus.TERMINATED;
        this.endTime = LocalDateTime.now();
        touch();
    }

    /**
     * 设置租户 ID
     *
     * @param tenantId 租户 ID
     */
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * 设置关联的工作流定义 ID
     *
     * @param definitionId 关联的工作流定义 ID
     */
    public void setDefinitionId(Long definitionId) {
        this.definitionId = definitionId;
    }

    /**
     * 设置流程编码
     *
     * @param processCode 流程编码
     */
    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    /**
     * 设置流程标题
     *
     * @param title 流程标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 设置流程状态
     *
     * @param status 流程状态
     */
    public void setStatus(ProcessStatus status) {
        this.status = status;
    }

    /**
     * 设置发起人用户 ID
     *
     * @param initiatorId 发起人用户 ID
     */
    public void setInitiatorId(Long initiatorId) {
        this.initiatorId = initiatorId;
    }

    /**
     * 设置发起人姓名
     *
     * @param initiatorName 发起人姓名
     */
    public void setInitiatorName(String initiatorName) {
        this.initiatorName = initiatorName;
    }

    /**
     * 设置业务主键
     *
     * @param businessKey 业务主键（关联业务系统的单据标识）
     */
    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    /**
     * 设置业务表单数据
     *
     * @param formData 业务表单数据（JSON 格式）
     */
    public void setFormData(String formData) {
        this.formData = formData;
    }

    /**
     * 设置当前节点 ID
     *
     * @param currentNodeId 当前节点 ID
     */
    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    /**
     * 设置流程开始时间
     *
     * @param startTime 流程开始时间
     */
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    /**
     * 设置流程结束时间
     *
     * @param endTime 流程结束时间
     */
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
