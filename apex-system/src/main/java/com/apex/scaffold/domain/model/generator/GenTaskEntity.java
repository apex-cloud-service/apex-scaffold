package com.apex.scaffold.domain.model.generator;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 代码生成器异步任务实体（代码生成器领域模型）
 *
 * <p>记录一次异步生成任务的提交参数、执行进度与终态，让向导执行从请求线程内的同步串行
 * 变为后台分批推进，前端可轮询进度并随时取消。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>认领式调度</strong>：{@link #status} 为 {@link GenTaskStatus#PENDING} 或
 *       租约过期的 {@link GenTaskStatus#RUNNING} 时才可被实例认领，避免多实例重复执行</li>
 *   <li><strong>进度计数</strong>：{@link #totalCount} 为表与模板组合总数，{@link #processedCount}
 *       为已处理数（含失败），{@link #failedCount} 为其中失败数，前端据此计算百分比</li>
 *   <li><strong>执行参数固化</strong>：{@link #previewToken} 与 {@link #overwriteFlag} 在提交时
 *       落库，后台执行不再依赖请求上下文</li>
 *   <li><strong>重试与租约</strong>：{@link #attempts} 超过上限的任务直接判定失败；
 *       {@link #lockedAt} 用于识别实例宕机后遗留的租约</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class GenTaskEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 生成配置 ID，执行时据此重新加载配置内容。 */
    private Long configId;

    /** 生成历史 ID，执行完成后回写；提交时为 {@code null}。 */
    private Long historyId;

    /** 任务状态字符串，取值见 {@link GenTaskStatus}。 */
    private String status;

    /** 待处理总数（表 × 模板）。 */
    private Integer totalCount;

    /** 已处理数（含失败）。 */
    private Integer processedCount;

    /** 已失败数。 */
    private Integer failedCount;

    /** 提交时校验通过的预览令牌，后台执行复用该令牌，不接受客户端再次提交。 */
    private String previewToken;

    /** 提交时的字段映射 JSON 快照，后台执行据此还原列改名与类型覆盖。 */
    private String fieldMappingsJson;

    /** 是否允许覆盖已存在文件：1 允许，0 拒绝。 */
    private Integer overwriteFlag;

    /** 提交操作人 ID，用于审计与产物归属回溯。 */
    private Long operatorId;

    /** 终态失败原因（截断存储）。 */
    private String errorMessage;

    /** 已执行次数，超过上限判定失败。 */
    private Integer attempts;

    /** 租约认领时间，用于识别实例宕机遗留的过期租约。 */
    private LocalDateTime lockedAt;

    /** 认领实例标识（实例 ID 与线程信息）。 */
    private String lockedBy;

    /** 完成时间（终态）。 */
    private LocalDateTime completedAt;

    /** 取消时间。 */
    private LocalDateTime canceledAt;

    private String tenantId;

    /**
     * 将状态字符串解析为枚举。
     *
     * @return 对应枚举，无法识别时返回 {@code null}
     */
    public GenTaskStatus statusEnum() {
        return GenTaskStatus.fromValue(status);
    }
}
