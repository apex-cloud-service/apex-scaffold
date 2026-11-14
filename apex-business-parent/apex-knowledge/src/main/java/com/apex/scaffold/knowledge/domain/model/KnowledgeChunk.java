package com.apex.scaffold.knowledge.domain.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识分块领域模型。
 *
 * <p>分块是检索的最小单元；向量本体不落库，仅存于向量索引文档中。
 * {@code contentHash} 用于在重新摄取时判断内容是否变化，避免无效重索引。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunk {

    /** 分块 ID（雪花 ID），同时作为向量索引文档主键。 */
    private Long id;

    /** 所属文档 ID。 */
    private Long documentId;

    /** 文档内序号（从 0 开始）。 */
    private Integer seq;

    /** 分块正文。 */
    private String content;

    /** 分块内容 SHA-256 摘要（十六进制），用于增量去重。 */
    private String contentHash;

    /** 预估 token 数（按字符数近似，非精确分词结果）。 */
    private Integer tokenEstimate;

    private LocalDateTime createTime;
}
