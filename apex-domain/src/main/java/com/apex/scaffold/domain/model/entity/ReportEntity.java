package com.apex.scaffold.domain.model.entity;

import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 报告聚合根实体
 *
 * <p>
 * 表示用户生成的内容分析报告，如照片风险评估报告。
 * 报告异步生成，状态包括待处理、处理中、已完成、失败。
 * </p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>报告生成是异步过程，需轮询状态</li>
 *   <li>报告数据以JSON格式存储</li>
 *   <li>处理失败可重试</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see PhotoEntity
 * @see BaseEntity
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ReportEntity extends BaseEntity {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 报告类型
     *
     * <p>
     * 标识报告的具体类型。
     * </p>
     *
     * @see ReportType
     */
    private String reportType;

    /**
     * 归属用户ID
     *
     * <p>
     * 报告归属的用户ID。
     * </p>
     */
    private Long ownerUserId;

    /**
     * 关联照片ID
     *
     * <p>
     * 报告关联的照片ID。
     * </p>
     */
    private Long photoId;

    /**
     * 报告状态
     *
     * <p>
     * 标识报告的当前处理状态。
     * </p>
     *
     * @see ReportStatus
     */
    private String status;

    /**
     * 处理进度
     *
     * <p>
     * 报告生成的进度百分比。
     * 范围：0~100
     * </p>
     */
    private Integer progress;

    /**
     * 报告数据JSON
     *
     * <p>
     * 报告内容的JSON格式存储。
     * 仅在状态为DONE时有效。
     * </p>
     */
    private String dataJson;

    /**
     * 生成时间
     *
     * <p>
     * 报告生成完成的时间戳。
     * </p>
     */
    private LocalDateTime createdAt;

    /**
     * 设置报告类型（仅供持久层和构造器使用）。
     *
     * @param value 报告类型
     */
    public void setReportType(String value) {
        this.reportType = value;
    }

    /**
     * 设置归属用户ID（仅供持久层和构造器使用）。
     *
     * @param value 用户ID
     */
    public void setOwnerUserId(Long value) {
        this.ownerUserId = value;
    }

    /**
     * 设置关联照片ID（仅供持久层和构造器使用）。
     *
     * @param value 照片ID
     */
    public void setPhotoId(Long value) {
        this.photoId = value;
    }

    /**
     * 设置报告状态（仅供持久层和构造器使用）。
     *
     * @param value 状态
     */
    public void setStatus(String value) {
        this.status = value;
    }

    /**
     * 设置处理进度（仅供持久层和构造器使用）。
     *
     * @param value 进度百分比
     */
    public void setProgress(Integer value) {
        this.progress = value;
    }

    /**
     * 设置报告数据JSON（仅供持久层和构造器使用）。
     *
     * @param value 数据JSON
     */
    public void setDataJson(String value) {
        this.dataJson = value;
    }

    /**
     * 设置生成时间（仅供持久层和构造器使用）。
     *
     * @param value 生成时间
     */
    public void setCreatedAt(LocalDateTime value) {
        this.createdAt = value;
    }

    /**
     * 默认构造函数
     */
    public ReportEntity() {
        super();
    }

    /**
     * 静态工厂方法：创建照片风险评估报告
     *
     * @param ownerUserId 归属用户ID
     * @param photoId 照片ID
     * @return 新创建的报告实体
     */
    public static ReportEntity createPhotoRiskReport(Long ownerUserId, Long photoId) {
        ReportEntity entity = new ReportEntity();
        entity.reportType = ReportType.PHOTO_RISK.getCode();
        entity.ownerUserId = ownerUserId;
        entity.photoId = photoId;
        entity.status = ReportStatus.PENDING.getCode();
        entity.progress = 0;
        entity.setTimestamps();
        return entity;
    }

    /**
     * 设置时间戳
     */
    private void setTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        this.setCreateTime(now);
        this.setUpdateTime(now);
        this.setDeleted(LogicalDeletion.NOT_DELETED.getCode());
    }

    /**
     * 领域行为：开始处理
     *
     * <p>
     * 将报告状态从PENDING改为RUNNING。
     * </p>
     */
    public void startProcessing() {
        this.status = ReportStatus.RUNNING.getCode();
        this.progress = 0;
        touch();
    }

    /**
     * 领域行为：更新处理进度
     *
     * @param progress 进度值（0-100）
     */
    public void updateProgress(int progress) {
        this.progress = Math.min(100, Math.max(0, progress));
        touch();
    }

    /**
     * 领域行为：完成处理
     *
     * @param dataJson 报告数据JSON
     */
    public void complete(String dataJson) {
        this.status = ReportStatus.DONE.getCode();
        this.progress = 100;
        this.dataJson = dataJson;
        this.createdAt = LocalDateTime.now();
        touch();
    }

    /**
     * 领域行为：标记为失败
     */
    public void markAsFailed() {
        this.status = ReportStatus.FAILED.getCode();
        touch();
    }

    /**
     * 领域行为：检查是否完成
     *
     * @return true-已完成，false-未完成
     */
    public boolean isCompleted() {
        return ReportStatus.DONE.getCode().equals(status);
    }

    /**
     * 领域行为：检查是否失败
     *
     * @return true-失败，false-未失败
     */
    public boolean isFailed() {
        return ReportStatus.FAILED.getCode().equals(status);
    }

    /**
     * 报告类型枚举
     */
    public enum ReportType {
        /** 照片风险评估 */
        PHOTO_RISK("PHOTO_RISK");

        private final String code;

        /**
         * 创建 {@code ReportType} 实例。
         *
         * @param code 枚举编码
         */
        ReportType(String code) {
            this.code = code;
        }

        /**
         * 获取枚举编码。
         * @return 枚举编码
         */
        public String getCode() {
            return code;
        }

        /**
         * 根据枚举编码解析举报类型。
         *
         * @param code 举报类型编码
         * @return 匹配的举报类型；编码为空或未匹配时返回 {@code null}
         */
        public static ReportType fromCode(String code) {
            if (code == null) {
                return null;
            }
            for (ReportType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * 报告状态枚举
     */
    public enum ReportStatus {
        /** 待处理 */
        PENDING("PENDING"),
        /** 处理中 */
        RUNNING("RUNNING"),
        /** 已完成 */
        DONE("DONE"),
        /** 失败 */
        FAILED("FAILED");

        private final String code;

        /**
         * 创建 {@code ReportStatus} 实例。
         *
         * @param code 枚举编码
         */
        ReportStatus(String code) {
            this.code = code;
        }

        /**
         * 获取枚举编码。
         * @return 枚举编码
         */
        public String getCode() {
            return code;
        }

        /**
         * 根据枚举编码解析举报处理状态。
         *
         * @param code 举报处理状态编码
         * @return 匹配的举报处理状态；编码为空或未匹配时返回 {@code null}
         */
        public static ReportStatus fromCode(String code) {
            if (code == null) {
                return null;
            }
            for (ReportStatus status : values()) {
                if (status.code.equals(code)) {
                    return status;
                }
            }
            return null;
        }
    }
}
