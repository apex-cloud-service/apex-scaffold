package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 举报工单领域实体
 *
 * <p>记录用户对其他用户、照片或消息的举报信息，包含举报原因、证据和处理状态。
 * 用于内容安全审核和社区管理。</p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>支持举报目标类型：USER/PHOTO/MESSAGE</li>
 *   <li>工单状态：PENDING/PROCESSING/RESOLVED/REJECTED</li>
 *   <li>证据以JSON格式存储，可为截图链接等</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see BaseEntity
 */
@Getter
@lombok.Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ReportTicketEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建举报工单实体对象。 */
    public ReportTicketEntity() {}

    /** 举报者用户 ID。 */
    private Long reporterUserId;

    /** 目标类型（USER/PHOTO/MESSAGE）。 */
    private String targetType;

    /** 目标 ID。 */
    private Long targetId;

    /** 举报原因。 */
    private String reason;

    /** 证据 JSON。 */
    private String evidence;

    /** 工单状态（PENDING/PROCESSING/RESOLVED/REJECTED）。 */
    private String status;

    /** 处理结果。 */
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
