package com.apex.scaffold.application.dto.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索结果 DTO
 *
 * <p>封装全文搜索返回的结果，供前端展示使用。
 *
 * @author Apex Team
 * @since 1.3.0
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
