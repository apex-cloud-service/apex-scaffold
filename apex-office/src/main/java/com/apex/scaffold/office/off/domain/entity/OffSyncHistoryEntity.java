package com.apex.scaffold.office.off.domain.entity;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 同步历史实体
 *
 * <p>用于记录与外部办公平台的数据同步历史，包括同步类型、状态、耗时和同步数据量等统计信息。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class OffSyncHistoryEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String syncType;
    private String syncSource;
    private String syncStatus;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer duration;
    private Integer deptCount;
    private Integer employeeCount;
    private String errorMessage;
    private String tenantId;

    public OffSyncHistoryEntity() {
        super();
    }

    /**
     * 设置同步类型，并刷新实体更新时间。
     *
     * @param syncType 本次同步的类型标识（如全量、增量）
     */
    public void setSyncType(String syncType) {
        this.syncType = syncType;
        touch();
    }

    /**
     * 设置同步来源，并刷新实体更新时间。
     *
     * @param syncSource 数据同步的外部平台标识（如 feishu、dingtalk、wechat）
     */
    public void setSyncSource(String syncSource) {
        this.syncSource = syncSource;
        touch();
    }

    /**
     * 设置同步状态，并刷新实体更新时间。
     *
     * @param syncStatus 同步的执行状态标识（成功、失败或进行中）
     */
    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
        touch();
    }

    /**
     * 设置同步开始时间，并刷新实体更新时间。
     *
     * @param startTime 本次同步任务的开始时间
     */
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
        touch();
    }

    /**
     * 设置同步结束时间，并刷新实体更新时间。
     *
     * @param endTime 本次同步任务的结束时间，进行中时为空
     */
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
        touch();
    }

    /**
     * 设置同步耗时，并刷新实体更新时间。
     *
     * @param duration 同步执行耗时（秒）
     */
    public void setDuration(Integer duration) {
        this.duration = duration;
        touch();
    }

    /**
     * 设置同步的部门数量，并刷新实体更新时间。
     *
     * @param deptCount 本次同步涉及的部门记录数
     */
    public void setDeptCount(Integer deptCount) {
        this.deptCount = deptCount;
        touch();
    }

    /**
     * 设置同步的员工数量，并刷新实体更新时间。
     *
     * @param employeeCount 本次同步涉及的员工记录数
     */
    public void setEmployeeCount(Integer employeeCount) {
        this.employeeCount = employeeCount;
        touch();
    }

    /**
     * 设置同步失败时的错误信息，并刷新实体更新时间。
     *
     * @param errorMessage 同步异常的描述信息，成功时为空
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        touch();
    }

    /**
     * 设置租户 ID，并刷新实体更新时间。
     *
     * @param tenantId 同步记录所属租户的唯一标识
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        touch();
    }
}
