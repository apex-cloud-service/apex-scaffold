package com.apex.scaffold.domain.model.monitor;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 体检快照异步导出任务实体（system 领域模型）。
 *
 * <p>当导出记录数超过同步上限时，改为提交一条后台任务；任务由定时调度器认领、
 * 分页生成 CSV 并落库，前端通过任务状态轮询并下载结果。
 *
 * <h2>设计说明</h2>
 * <pre>
 * PENDING ──claim──▶ RUNNING ──成功──▶ COMPLETED
 *                        └──失败──▶ FAILED ──重试──▶ RUNNING
 * </pre>
 * RUNNING 超过租约时间未更新时，其他实例可重新认领，避免实例崩溃后任务永久挂起。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class HealthExportTaskEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 任务状态：待处理。 */
    public static final String STATUS_PENDING = "PENDING";

    /** 任务状态：执行中。 */
    public static final String STATUS_RUNNING = "RUNNING";

    /** 任务状态：已完成。 */
    public static final String STATUS_COMPLETED = "COMPLETED";

    /** 任务状态：已失败。 */
    public static final String STATUS_FAILED = "FAILED";

    /**
     * 创建空任务实体。
     *
     * <p>显式初始化时间戳，因为自动填充只发生在 PO 上。</p>
     */
    public HealthExportTaskEntity() {
        initTimestamps();
    }

    /** 导出条件：生成时间下界（含）。 */
    private LocalDateTime queryFrom;

    /** 导出条件：生成时间上界（含）。 */
    private LocalDateTime queryTo;

    /** 导出条件：总体结论过滤。 */
    private String queryStatus;

    /** 任务状态。 */
    private String status;

    /** 已尝试次数。 */
    private Integer attempts;

    /** 最近一次失败原因。 */
    private String lastError;

    /** 生成文件名。 */
    private String fileName;

    /** 导出记录数。 */
    private Integer rowCount;

    /** 导出文件内容。 */
    private byte[] content;

    /** 认领时间。 */
    private LocalDateTime lockedAt;

    /** 认领实例标识。 */
    private String lockedBy;

    /** 完成时间。 */
    private LocalDateTime completedAt;

    /** 租户标识。 */
    private String tenantId;
}
