package com.apex.scaffold.knowledge.domain.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识文档领域模型。
 *
 * <p>一份文档对应对象存储中的一个对象与数据库一行主档；正文文本、分块与向量
 * 索引均为摄取管线的派生产物，通过 {@code parseStatus} 与 {@code indexStatus}
 * 两个状态机跟踪，失败原因写入 {@code parseError} 供管理端诊断。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {

    /** 文档 ID（雪花 ID）。 */
    private Long id;

    /** 所属空间 ID。 */
    private Long spaceId;

    /** 文档标题（默认取上传文件名）。 */
    private String title;

    /** 来源类型。 */
    private SourceType sourceType;

    /** 对象存储内容类型（如 application/pdf）。 */
    private String contentType;

    /** 对象存储键（bucket 内唯一）。 */
    private String objectKey;

    /** 对象大小（字节）。 */
    private Long fileSize;

    /** 解析出的纯文本正文（摄取产物，可随对象一起删除）。 */
    private String contentText;

    /** 解析状态机。 */
    private ParseStatus parseStatus;

    /** 解析失败原因（白名单摘要，不含对象内容）。 */
    private String parseError;

    /** 分块数量。 */
    private Integer chunkCount;

    /** 索引状态机。 */
    private IndexStatus indexStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 文档来源类型。 */
    public enum SourceType {
        /** 用户上传文件。 */
        UPLOAD,
        /** 在线录入纯文本。 */
        MANUAL
    }

    /** 解析状态机：PENDING → PARSING → PARSED / FAILED。 */
    public enum ParseStatus {
        /** 已登记，待解析。 */
        PENDING,
        /** 解析中。 */
        PARSING,
        /** 解析完成。 */
        PARSED,
        /** 解析失败（原因见 parseError）。 */
        FAILED
    }

    /**
     * 索引状态机：NONE → PENDING → INDEXED / INDEXED_LEXICAL / FAILED。
     *
     * <p>INDEXED 表示已写入向量索引；INDEXED_LEXICAL 表示仅写入词法索引
     * （embedding 供应商未启用）；FAILED 需人工或校准 Job 重试。
     */
    public enum IndexStatus {
        /** 未进入索引流程（解析尚未完成或索引未启用）。 */
        NONE,
        /** 待索引。 */
        PENDING,
        /** 已写入向量+词法索引。 */
        INDEXED,
        /** 仅写入词法索引。 */
        INDEXED_LEXICAL,
        /** 索引失败。 */
        FAILED
    }
}
