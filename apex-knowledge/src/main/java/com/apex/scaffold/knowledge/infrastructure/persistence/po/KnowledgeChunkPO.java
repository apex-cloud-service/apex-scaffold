package com.apex.scaffold.knowledge.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 知识分块持久化对象，对应表 {@code kb_chunk}。
 *
 * <p>向量本体不落库；{@code content_hash} 用于增量重摄取判定。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("kb_chunk")
public class KnowledgeChunkPO extends BasePO {

    private static final long serialVersionUID = 1L;

    /** 所属文档 ID。 */
    private Long documentId;

    /** 文档内序号。 */
    private Integer seq;

    /** 分块正文。 */
    private String content;

    /** 内容 SHA-256 摘要（十六进制）。 */
    private String contentHash;

    /** 预估 token 数。 */
    private Integer tokenEstimate;

    /** 租户 ID。 */
    private String tenantId;
}
