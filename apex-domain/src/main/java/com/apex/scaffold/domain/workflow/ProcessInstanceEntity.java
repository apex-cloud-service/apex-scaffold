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

    public void start(String startNodeId) {
        this.status = ProcessStatus.RUNNING;
        this.currentNodeId = startNodeId;
        this.startTime = LocalDateTime.now();
        touch();
    }

    public void moveToNode(String nodeId) {
        this.currentNodeId = nodeId;
        touch();
    }

    public void approve() {
        this.status = ProcessStatus.APPROVED;
        this.endTime = LocalDateTime.now();
        touch();
    }

    public void reject() {
        this.status = ProcessStatus.REJECTED;
        this.endTime = LocalDateTime.now();
        touch();
    }

    public void cancel() {
        this.status = ProcessStatus.CANCELLED;
        this.endTime = LocalDateTime.now();
        touch();
    }

    public void terminate() {
        this.status = ProcessStatus.TERMINATED;
        this.endTime = LocalDateTime.now();
        touch();
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public void setDefinitionId(Long definitionId) {
        this.definitionId = definitionId;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setStatus(ProcessStatus status) {
        this.status = status;
    }

    public void setInitiatorId(Long initiatorId) {
        this.initiatorId = initiatorId;
    }

    public void setInitiatorName(String initiatorName) {
        this.initiatorName = initiatorName;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public void setFormData(String formData) {
        this.formData = formData;
    }

    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
