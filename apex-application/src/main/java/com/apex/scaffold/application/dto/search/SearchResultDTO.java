package com.apex.scaffold.application.dto.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索结果 DTO。
 *
 * <p>封装全文搜索返回的单条结果，供前端列表展示使用。</p>
 *
 * <p>主要字段：</p>
 * <ul>
 *   <li>{@code resourceType} - 资源类型</li>
 *   <li>{@code resourceId} - 资源 ID</li>
 *   <li>{@code title} - 主标题</li>
 *   <li>{@code subtitle} - 副标题</li>
 *   <li>{@code description} - 描述信息</li>
 *   <li>{@code highlightSnippet} - 高亮片段（HTML 格式）</li>
 *   <li>{@code score} - 相关性评分</li>
 *   <li>{@code status} - 状态（1-启用，0-禁用）</li>
 *   <li>{@code createdAt}/{@code updatedAt} - 创建与更新时间（格式化字符串）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultDTO {

    /**
     * 资源类型
     */
    private String resourceType;

    /**
     * 资源ID
     */
    private String resourceId;

    /**
     * 主标题
     */
    private String title;

    /**
     * 副标题
     */
    private String subtitle;

    /**
     * 描述信息
     */
    private String description;

    /**
     * 高亮片段（HTML格式）
     */
    private String highlightSnippet;

    /**
     * 相关性评分
     */
    private Double score;

    /**
     * 状态：1启用 0禁用
     */
    private Integer status;

    /**
     * 创建时间（格式化字符串）
     */
    private String createdAt;

    /**
     * 更新时间（格式化字符串）
     */
    private String updatedAt;
}
