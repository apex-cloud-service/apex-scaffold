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

    /**
     * 设置文档标题。仅供持久层回填使用。
     *
     * @param value 文档标题
     */
    public void setTitle(String value) {
        this.title = value;
    }

    /**
     * 设置文档类型。仅供持久层回填使用。
     *
     * @param value 文档类型（contract/report/proposal/manual/other）
     */
    public void setDocType(String value) {
        this.docType = value;
    }

    /**
     * 设置文档分类。仅供持久层回填使用。
     *
     * @param value 文档分类（tech/business/finance/hr）
     */
    public void setCategory(String value) {
        this.category = value;
    }

    /**
     * 设置文档正文内容。仅供持久层回填使用。
     *
     * @param value 文档的正文内容
     */
    public void setContent(String value) {
        this.content = value;
    }

    /**
     * 设置文档文件访问地址。仅供持久层回填使用。
     *
     * @param value 关联文件的对象存储 URL
     */
    public void setFileUrl(String value) {
        this.fileUrl = value;
    }

    /**
     * 设置文件大小。仅供持久层回填使用。
     *
     * @param value 关联文件的大小（字节）
     */
    public void setFileSize(Long value) {
        this.fileSize = value;
    }

    /**
     * 设置文档作者 ID。仅供持久层回填使用。
     *
     * @param value 文档创建者的用户 ID
     */
    public void setAuthorId(Long value) {
        this.authorId = value;
    }

    /**
     * 设置文档作者姓名。仅供持久层回填使用。
     *
     * @param value 文档创建者的姓名
     */
    public void setAuthorName(String value) {
        this.authorName = value;
    }

    /**
     * 设置文档状态。仅供持久层回填使用。
     *
     * @param value 文档状态码（0-草稿 1-正常 2-已归档）
     */
    public void setStatus(Integer value) {
        this.status = value;
    }

    /**
     * 设置文档标签列表。仅供持久层回填使用。
     *
     * @param value 文档的标签集合
     */
    public void setTags(List<String> value) {
        this.tags = value;
    }

    /**
     * 设置浏览计数。仅供持久层回填使用。
     *
     * @param value 文档累计浏览次数
     */
    public void setViewCount(Integer value) {
        this.viewCount = value;
    }

    /**
     * 设置下载计数。仅供持久层回填使用。
     *
     * @param value 文档累计下载次数
     */
    public void setDownloadCount(Integer value) {
        this.downloadCount = value;
    }

    /**
     * 设置租户 ID。仅供持久层回填使用。
     *
     * @param value 文档所属租户的唯一标识
     */
    public void setTenantId(String value) {
        this.tenantId = value;
    }

    public DocumentEntity() {
        super();
    }

    /**
     * 创建草稿状态的文档实体。
     *
     * <p>工厂方法，初始化文档基本信息并将状态置为草稿，浏览和下载计数归零。</p>
     *
     * @param title 文档标题
     * @param docType 文档类型（contract/report/proposal/manual/other）
     * @param category 文档分类（tech/business/finance/hr）
     * @param content 文档的正文内容
     * @param authorId 文档创建者的用户 ID
     * @param authorName 文档创建者的姓名
     * @param tenantId 文档所属租户的唯一标识
     * @return 初始化完成的草稿状态文档实体
     */
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

    /**
     * 更新文档的基本信息，并刷新实体更新时间。
     *
     * @param title 新的文档标题
     * @param docType 新的文档类型（contract/report/proposal/manual/other）
     * @param category 新的文档分类（tech/business/finance/hr）
     * @param content 新的文档正文内容
     * @param tags 新的文档标签集合
     */
    public void updateInfo(String title, String docType, String category, String content, List<String> tags) {
        this.title = title;
        this.docType = docType;
        this.category = category;
        this.content = content;
        this.tags = tags;
        touch();
    }

    /**
     * 为文档关联文件附件，并刷新实体更新时间。
     *
     * @param fileUrl 附件的对象存储访问 URL
     * @param fileSize 附件文件大小（字节）
     */
    public void attachFile(String fileUrl, Long fileSize) {
        this.fileUrl = fileUrl;
        this.fileSize = fileSize;
        touch();
    }

    /**
     * 发布文档，将状态从草稿切换为正常，并刷新实体更新时间。
     */
    public void publish() {
        this.status = DocumentStatus.NORMAL.getCode();
        touch();
    }

    /**
     * 归档文档，将状态切换为已归档，并刷新实体更新时间。
     */
    public void archive() {
        this.status = DocumentStatus.ARCHIVED.getCode();
        touch();
    }

    /**
     * 浏览计数加一，并刷新实体更新时间。
     */
    public void incrementViewCount() {
        this.viewCount = (this.viewCount == null ? 0 : this.viewCount) + 1;
        touch();
    }

    /**
     * 下载计数加一，并刷新实体更新时间。
     */
    public void incrementDownloadCount() {
        this.downloadCount = (this.downloadCount == null ? 0 : this.downloadCount) + 1;
        touch();
    }

    /**
     * 判断文档是否处于草稿状态。
     *
     * @return true-草稿状态，false-其他状态
     */
    public boolean isDraft() {
        return status != null && status.equals(DocumentStatus.DRAFT.getCode());
    }

    /**
     * 判断文档是否处于正常（已发布）状态。
     *
     * @return true-正常状态，false-其他状态
     */
    public boolean isNormal() {
        return status != null && status.equals(DocumentStatus.NORMAL.getCode());
    }

    /**
     * 判断文档是否处于已归档状态。
     *
     * @return true-已归档状态，false-其他状态
     */
    public boolean isArchived() {
        return status != null && status.equals(DocumentStatus.ARCHIVED.getCode());
    }

    public enum DocumentStatus {
        /** 草稿 */
        DRAFT(0),
        /** 正常 */
        NORMAL(1),
        /** 已归档 */
        ARCHIVED(2);

        private final Integer code;

        DocumentStatus(Integer code) {
            this.code = code;
        }

        /**
         * 获取文档状态的数据库存储码。
         *
         * @return 状态码（0-草稿 1-正常 2-已归档）
         */
        public Integer getCode() {
            return code;
        }
    }

    public enum DocType {
        /** 合同 */
        CONTRACT("contract"),
        /** 报告 */
        REPORT("report"),
        /** 提案 */
        PROPOSAL("proposal"),
        /** 手册 */
        MANUAL("manual"),
        /** 其他 */
        OTHER("other");

        private final String code;

        DocType(String code) {
            this.code = code;
        }

        /**
         * 获取文档类型的字符串编码。
         *
         * @return 类型编码（contract/report/proposal/manual/other）
         */
        public String getCode() {
            return code;
        }
    }

    public enum Category {
        /** 技术 */
        TECH("tech"),
        /** 业务 */
        BUSINESS("business"),
        /** 财务 */
        FINANCE("finance"),
        /** 人力资源 */
        HR("hr");

        private final String code;

        Category(String code) {
            this.code = code;
        }

        /**
         * 获取文档分类的字符串编码。
         *
         * @return 分类编码（tech/business/finance/hr）
         */
        public String getCode() {
            return code;
        }
    }
}
