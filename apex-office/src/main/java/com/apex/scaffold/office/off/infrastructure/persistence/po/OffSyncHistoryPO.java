package com.apex.scaffold.office.off.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 办公数据同步历史持久化对象
 *
 * <p>对应数据库表 {@code off_sync_history}，记录从第三方办公平台同步数据的历史记录和执行状态。
 * 继承 {@link BasePO} 获得通用字段（ID、创建时间、更新时间、逻辑删除等）。
 *
 * <h2>核心字段</h2>
 * <ul>
 *   <li>{@link #syncType} - 同步类型（EMPLOYEE=员工、DEPARTMENT=部门、FULL=全量同步）</li>
 *   <li>{@link #syncSource} - 同步来源平台（FEISHU=飞书、DINGTALK=钉钉、WECOM=企业微信）</li>
 *   <li>{@link #syncStatus} - 同步状态（SUCCESS=成功、FAILED=失败、IN_PROGRESS=进行中）</li>
 *   <li>{@link #startTime} - 同步开始时间</li>
 *   <li>{@link #endTime} - 同步结束时间</li>
 *   <li>{@link #duration} - 同步耗时（秒）</li>
 *   <li>{@link #deptCount} - 同步的部门数量</li>
 *   <li>{@link #employeeCount} - 同步的员工数量</li>
 *   <li>{@link #errorMessage} - 错误信息（同步失败时记录）</li>
 *   <li>{@link #tenantId} - 租户标识（多租户隔离）</li>
 * </ul>
 *
 * <h2>同步类型</h2>
 * <ul>
 *   <li>EMPLOYEE：仅同步员工信息</li>
 *   <li>DEPARTMENT：仅同步部门组织架构</li>
 *   <li>FULL：全量同步（包含部门和员工）</li>
 *   <li>INCREMENTAL：增量同步（仅同步变更数据）</li>
 * </ul>
 *
 * <h2>同步状态</h2>
 * <ul>
 *   <li>IN_PROGRESS：同步进行中</li>
 *   <li>SUCCESS：同步成功完成</li>
 *   <li>FAILED：同步失败</li>
 *   <li>PARTIAL_SUCCESS：部分成功（有失败记录但整体完成）</li>
 * </ul>
 *
 * <h2>业务场景</h2>
 * <ul>
 *   <li>同步记录：记录每次数据同步的执行情况</li>
 *   <li>失败追溯：查询同步失败的历史记录和原因</li>
 *   <li>性能监控：统计同步耗时和成功率</li>
 *   <li>数据对账：通过同步记录进行数据一致性检查</li>
 *   <li>定时任务：定时任务执行时创建同步历史记录</li>
 * </ul>
 *
 * <h2>统计指标</h2>
 * <ul>
 *   <li>同步成功率 = 成功次数 / 总次数</li>
 *   <li>平均耗时 = 总耗时 / 同步次数</li>
 *   <li>数据量统计 = 部门数量 + 员工数量</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>同步历史记录通常保留而不物理删除，以维护审计完整性</li>
 *   <li>可定期归档或清理过期历史记录（如 90 天前的记录）</li>
 *   <li>同步耗时 {@code duration} 用于性能监控和优化</li>
 *   <li>租户隔离通过 {@code tenantId} 字段实现</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see BasePO
 * @see OffEmployeePO
 * @see OffDepartmentPO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("off_sync_history")
public class OffSyncHistoryPO extends BasePO {

    @TableField("sync_type")
    private String syncType;

    @TableField("sync_source")
    private String syncSource;

    @TableField("sync_status")
    private String syncStatus;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("duration")
    private Integer duration;

    @TableField("dept_count")
    private Integer deptCount;

    @TableField("employee_count")
    private Integer employeeCount;

    @TableField("error_message")
    private String errorMessage;

    @TableField("tenant_id")
    private String tenantId;
}
