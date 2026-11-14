package com.apex.scaffold.domain.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import lombok.Getter;

/**
 * 报告生成完成领域事件
 *
 * <p>当用户的分析报告（审美DNA、动物特质等类型报告）生成完成时发布的事件
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public class ReportGeneratedEvent extends AbstractDomainEvent {

    /** 报告 ID */
    private final Long reportId;

    /** 报告所属用户 ID */
    private final Long ownerUserId;

    /** 关联照片 ID */
    private final Long photoId;

    /** 报告类型（如：AESTHETIC_DNA, ANIMAL 等） */
    private final String reportType;

    /**
     * 创建报告生成完成事件
     *
     * @param reportId    报告 ID
     * @param ownerUserId 报告所属用户 ID
     * @param photoId     关联照片 ID
     * @param reportType  报告类型
     */
    public ReportGeneratedEvent(Long reportId, Long ownerUserId, Long photoId, String reportType) {
        super("REPORT_GENERATED", null);
        this.reportId = reportId;
        this.ownerUserId = ownerUserId;
        this.photoId = photoId;
        this.reportType = reportType;
    }

    /**
     * 获取事件源ID。
     *
     * @return 报告ID的字符串表示
     * @since 1.0.0
     */
    @Override
    public String getSourceId() {
        return String.valueOf(reportId);
    }
}
