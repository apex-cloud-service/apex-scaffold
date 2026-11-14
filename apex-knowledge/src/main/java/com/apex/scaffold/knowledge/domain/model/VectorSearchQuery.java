package com.apex.scaffold.knowledge.domain.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识检索条件。
 *
 * <p>{@code queryEmbedding} 为空时退化为词法检索；{@code tenantId} 强制参与过滤，
 * 不允许为空（fail-closed）。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchQuery {

    /** 检索关键词。 */
    private String keyword;

    /** 查询向量（可为空）。 */
    private float[] queryEmbedding;

    /** 租户 ID（强制过滤）。 */
    private String tenantId;

    /** 限定空间 ID 列表（可为空表示租户内全部正常空间）。 */
    private List<Long> spaceIds;

    /** 偏移量。 */
    private int offset;

    /** 返回条数上限。 */
    private int limit;

    /** 语义占比（0-1，仅 hybrid 模式生效）。 */
    private double semanticRatio;
}
