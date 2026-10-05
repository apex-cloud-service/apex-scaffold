package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 举报工单持久化对象。
 *
 * <p>映射数据库表 biz_report_ticket，记录用户举报信息，
 * 包括举报者、目标类型与ID、原因、证据、工单状态和处理结果。
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
@TableName("biz_report_ticket")
public class ReportTicketPO extends BasePO {

    /**
     * 创建举报工单实体对象。
     *
     */

    /**
     * 举报者用户 ID。
     */
    @TableField("reporter_user_id")
    private Long reporterUserId;

    /**
     * 目标类型（如 USER / PHOTO / MESSAGE）。
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 目标 ID。
     */
    @TableField("target_id")
    private Long targetId;

    /**
     * 举报原因。
     */
    @TableField("reason")
    private String reason;

    /**
     * 证据 JSON 数据。
     */
    @TableField("evidence")
    private String evidence;

    /**
     * 工单状态（如 PENDING / PROCESSING / RESOLVED / REJECTED）。
     */
    @TableField("status")
    private String status;

    /**
     * 处理结果。
     */
    @TableField("result")
    private String result;

    /**
     * 兼容旧字段命名：evidenceJson。
     *
     * @return 证据 JSON
     */
    public String getEvidenceJson() {
        return getEvidence();
    }

    /**
     * 兼容旧字段命名：evidenceJson。
     *
     * @param evidenceJson 证据 JSON
     */
    public void setEvidenceJson(String evidenceJson) {
        setEvidence(evidenceJson);
    }
}
