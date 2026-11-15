package com.apex.scaffold.knowledge.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 知识文档持久化对象，对应表 {@code kb_document}。
 *
 * <p>{@code contentText} 为摄取产物正文（大文本列）；解析与索引状态机
 * 详见领域模型 {@code KnowledgeDocument}。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("kb_document")
public class KnowledgeDocumentPO extends BasePO {

    private static final long serialVersionUID = 1L;

    /** 所属空间 ID。 */
    private Long spaceId;

    /** 文档标题。 */
    private String title;

    /** 来源类型：UPLOAD/MANUAL。 */
    private String sourceType;

    /** 对象存储内容类型。 */
    private String contentType;

    /** 对象存储键。 */
    private String objectKey;

    /** 上传对象所在存储提供商（如 local/s3），历史记录可能为空。 */
    private String storageProvider;

    /** 上传对象所在桶名，历史记录可能为空。 */
    private String storageBucket;

    /** 对象是否由知识域独占（1=是），历史未确认时不得自动删除。 */
    private Integer objectOwned;

    /** 索引端点快照，用于定位原索引目标。 */
    private String indexEndpoint;

    /** 索引名快照，用于定向清理与重建。 */
    private String indexName;

    /** 是否存在必须确认的索引清理（1=是）。 */
    private Integer indexRequired;

    /** 对象大小（字节）。 */
    private Long fileSize;

    /** 解析出的纯文本正文。 */
    private String contentText;

    /** 解析状态：PENDING/PARSING/PARSED/FAILED。 */
    private String parseStatus;

    /** 解析失败原因摘要。 */
    private String parseError;

    /** 分块数量。 */
    private Integer chunkCount;

    /** 索引状态：NONE/PENDING/INDEXED/INDEXED_LEXICAL/FAILED。 */
    private String indexStatus;

    /** 租户 ID。 */
    private String tenantId;
}
