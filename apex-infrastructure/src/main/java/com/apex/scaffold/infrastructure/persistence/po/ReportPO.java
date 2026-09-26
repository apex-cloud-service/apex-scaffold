package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 报告持久化对象。
 *
 * <p>映射数据库表 biz_report，记录分析报告的生成状态、进度和数据。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("biz_report")
public class ReportPO extends BasePO {

    /**
     * 创建报告持久化对象。
     */

    /** 报告类型。 */
    @TableField("report_type")
    private String reportType;

    /** 归属用户ID。 */
    @TableField("owner_user_id")
    private Long ownerUserId;

    /** 关联照片ID。 */
    @TableField("photo_id")
    private Long photoId;

    /** 状态（PENDING/RUNNING/DONE/FAILED）。 */
    @TableField("status")
    private String status;

    /** 进度（0~100）。 */
    @TableField("progress")
    private Integer progress;

    /** 报告数据JSON。 */
    @TableField("data_json")
    private String dataJson;

    /** 生成时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
