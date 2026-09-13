package com.apex.scaffold.domain.model.entity;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 文档领域实体。
 *
 * <p>文档聚合根，封装文档的核心业务逻辑和状态转换。文档可以是合同、报告、提案、手册等类型，
 * 支持草稿、正常、归档三种状态的生命周期管理。提供文档信息更新、文件附件关联、
 * 发布、归档、浏览计数、下载计数等领域行为。
 *
 * <h2>文档生命周期</h2>
 * <ol>
 *   <li><b>草稿（DRAFT）</b>：文档创建后的初始状态，可编辑修改</li>
 *   <li><b>正常（NORMAL）</b>：文档发布后的状态，对外可见</li>
 *   <li><b>归档（ARCHIVED）</b>：文档归档后的状态，不再使用</li>
 * </ol>
 *
 * <h2>核心行为</h2>
 * <ul>
 *   <li>{@link #create(String, String, String, String, Long, String, String)} - 创建草稿文档</li>
 *   <li>{@link #updateInfo(String, String, String, String, List)} - 更新文档信息</li>
 *   <li>{@link #attachFile(String, Long)} - 关联文件附件</li>
 *   <li>{@link #publish()} - 发布文档（草稿 → 正常）</li>
 *   <li>{@link #archive()} - 归档文档（正常 → 归档）</li>
 *   <li>{@link #incrementViewCount()} - 增加浏览计数</li>
 *   <li>{@link #incrementDownloadCount()} - 增加下载计数</li>
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li>使用工厂方法 {@code create} 创建文档，确保创建时的业务规则一致性</li>
 *   <li>所有状态变更通过领域方法实现，避免直接修改状态字段</li>
 *   <li>继承 {@link BaseEntity}，自动获得 ID、创建时间、更新时间等通用字段</li>
 *   <li>支持多租户隔离，通过 tenantId 字段实现租户数据隔离</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see BaseEntity
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class DocumentEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String title;
    private String docType;
    private String category;
    private String content;
    private String fileUrl;
    private Long fileSize;
    private Long authorId;
    private String authorName;
    private Integer status;
    private List<String> tags;
    private Integer viewCount;
    private Integer downloadCount;
    private String tenantId;

    public void setTitle(String value) {
        this.title = value;
    }

    public void setDocType(String value) {
        this.docType = value;
    }

    public void setCategory(String value) {
        this.category = value;
    }

    public void setContent(String value) {
        this.content = value;
    }

    public void setFileUrl(String value) {
        this.fileUrl = value;
    }

    public void setFileSize(Long value) {
        this.fileSize = value;
    }

    public void setAuthorId(Long value) {
        this.authorId = value;
    }

    public void setAuthorName(String value) {
        this.authorName = value;
    }

    public void setStatus(Integer value) {
        this.status = value;
    }

    public void setTags(List<String> value) {
        this.tags = value;
    }

    public void setViewCount(Integer value) {
        this.viewCount = value;
    }

    public void setDownloadCount(Integer value) {
        this.downloadCount = value;
    }

    public void setTenantId(String value) {
        this.tenantId = value;
    }

    public DocumentEntity() {
        super();
    }

    public static DocumentEntity create(
            String title,
            String docType,
            String category,
            String content,
            Long authorId,
            String authorName,
            String tenantId) {
        DocumentEntity entity = new DocumentEntity();
        entity.title = title;
        entity.docType = docType;
        entity.category = category;
        entity.content = content;
        entity.authorId = authorId;
        entity.authorName = authorName;
        entity.tenantId = tenantId;
        entity.status = DocumentStatus.DRAFT.getCode();
        entity.viewCount = 0;
        entity.downloadCount = 0;
        entity.initTimestamps();
        return entity;
    }

    public void updateInfo(String title, String docType, String category, String content, List<String> tags) {
        this.title = title;
        this.docType = docType;
        this.category = category;
        this.content = content;
        this.tags = tags;
        touch();
    }

    public void attachFile(String fileUrl, Long fileSize) {
        this.fileUrl = fileUrl;
        this.fileSize = fileSize;
        touch();
    }

    public void publish() {
        this.status = DocumentStatus.NORMAL.getCode();
        touch();
    }

    public void archive() {
        this.status = DocumentStatus.ARCHIVED.getCode();
        touch();
    }

    public void incrementViewCount() {
        this.viewCount = (this.viewCount == null ? 0 : this.viewCount) + 1;
        touch();
    }

    public void incrementDownloadCount() {
        this.downloadCount = (this.downloadCount == null ? 0 : this.downloadCount) + 1;
        touch();
    }

    public boolean isDraft() {
        return status != null && status.equals(DocumentStatus.DRAFT.getCode());
    }

    public boolean isNormal() {
        return status != null && status.equals(DocumentStatus.NORMAL.getCode());
    }

    public boolean isArchived() {
        return status != null && status.equals(DocumentStatus.ARCHIVED.getCode());
    }

    public enum DocumentStatus {
        DRAFT(0),
        NORMAL(1),
        ARCHIVED(2);

        private final Integer code;

        DocumentStatus(Integer code) {
            this.code = code;
        }

        public Integer getCode() {
            return code;
        }
    }

    public enum DocType {
        CONTRACT("contract"),
        REPORT("report"),
        PROPOSAL("proposal"),
        MANUAL("manual"),
        OTHER("other");

        private final String code;

        DocType(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    public enum Category {
        TECH("tech"),
        BUSINESS("business"),
        FINANCE("finance"),
        HR("hr");

        private final String code;

        Category(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
