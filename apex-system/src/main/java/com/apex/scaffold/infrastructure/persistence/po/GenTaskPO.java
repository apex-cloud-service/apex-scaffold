package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 代码生成器异步生成任务持久化对象。
 *
 * <p>对应数据库表 {@code gen_task}，记录一次向导受控执行的后台任务状态与进度，
 * 是前端轮询进度、取消任务与失败重试的唯一定位依据。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.5.0
 * @see com.apex.scaffold.domain.model.generator.GenTaskEntity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("gen_task")
public class GenTaskPO extends BasePO {

    /** 生成配置 ID。 */
    private Long configId;

    /** 生成历史 ID，任务完成后回写。 */
    private Long historyId;

    /** 任务状态（PENDING/RUNNING/COMPLETED/FAILED/CANCELED）。 */
    private String status;

    /** 总处理单元数。 */
    private Integer totalCount;

    /** 已处理单元数。 */
    private Integer processedCount;

    /** 失败单元数。 */
    private Integer failedCount;

    /** 提交时生成的预览令牌，与提交时的预览结果绑定。 */
    private String previewToken;

    /** 提交时的字段映射 JSON 快照。 */
    private String fieldMappingsJson;

    /** 是否允许覆盖（1=允许，0=禁止）。 */
    private Integer overwriteFlag;

    /** 提交人用户 ID。 */
    private Long operatorId;

    /** 失败原因或最后一次单元错误摘要。 */
    private String errorMessage;

    /** 已认领次数，用于限制重试。 */
    private Integer attempts;

    /** 认领时间，作为租约起点判断任务是否已被遗弃。 */
    private LocalDateTime lockedAt;

    /** 认领实例标识。 */
    private String lockedBy;

    /** 完成（含失败、取消）时间。 */
    private LocalDateTime completedAt;

    /** 取消时间。 */
    private LocalDateTime canceledAt;

    /** 租户标识。 */
    private String tenantId;
}
