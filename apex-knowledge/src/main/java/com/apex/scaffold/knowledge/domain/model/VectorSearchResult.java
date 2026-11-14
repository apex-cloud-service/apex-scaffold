package com.apex.scaffold.knowledge.domain.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识检索结果。
 *
 * @param hits 命中分块列表
 * @param total 命中总数（用于分页展示）
 * @param hybridUsed 是否实际启用了向量语义检索（false 表示词法兜底）
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchResult {

    private List<VectorSearchHit> hits;

    private long total;

    private boolean hybridUsed;
}
