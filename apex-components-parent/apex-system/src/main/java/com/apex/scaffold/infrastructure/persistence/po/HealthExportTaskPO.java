package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 体检快照异步导出任务持久化对象。
 *
 * <p>对应数据库表 {@code ops_health_export_task}。{@code content} 为 CSV 二进制，
 * 使用 LONGBLOB/BYTEA 承载；MyBatis 对 {@code byte[]} 默认映射为 BLOB，无需额外类型处理器。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 * @see com.apex.scaffold.domain.model.monitor.HealthExportTaskEntity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("ops_health_export_task")
public class HealthExportTaskPO extends BasePO {

    /** 导出条件：生成时间下界（含）。 */
    @TableField("query_from")
    private LocalDateTime queryFrom;

    /** 导出条件：生成时间上界（含）。 */
    @TableField("query_to")
    private LocalDateTime queryTo;

    /** 导出条件：总体结论过滤。 */
    @TableField("query_status")
    private String queryStatus;

    /** 任务状态。 */
    @TableField("status")
    private String status;

    /** 已尝试次数。 */
    @TableField("attempts")
    private Integer attempts;

    /** 最近一次失败原因。 */
    @TableField("last_error")
    private String lastError;

    /** 生成文件名。 */
    @TableField("file_name")
    private String fileName;

    /** 导出记录数。 */
    @TableField("row_count")
    private Integer rowCount;

    /** 导出文件内容。 */
    @TableField("content")
    private byte[] content;

    /** 认领时间。 */
    @TableField("locked_at")
    private LocalDateTime lockedAt;

    /** 认领实例标识。 */
    @TableField("locked_by")
    private String lockedBy;

    /** 完成时间。 */
    @TableField("completed_at")
    private LocalDateTime completedAt;

    /** 租户标识。 */
    @TableField("tenant_id")
    private String tenantId;
}
