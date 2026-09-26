package com.apex.scaffold.application.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "搜索结果DTO，封装全文搜索返回的单条结果，供前端列表展示")
public class SearchResultDTO {

    /**
     * 资源类型
     */
    @Schema(description = "资源类型：user(用户)/role(角色)/menu(菜单)/dept(部门)", example = "user")
    private String resourceType;

    /**
     * 资源ID
     */
    @Schema(description = "资源ID", example = "1001")
    private String resourceId;

    /**
     * 主标题
     */
    @Schema(description = "主标题（资源名称）", example = "张三")
    private String title;

    /**
     * 副标题
     */
    @Schema(description = "副标题（补充说明信息）", example = "zhangsan@example.com")
    private String subtitle;

    /**
     * 描述信息
     */
    @Schema(description = "描述信息", example = "系统管理员账号")
    private String description;

    /**
     * 高亮片段（HTML格式）
     */
    @Schema(description = "命中关键词的高亮片段（HTML格式）", example = "张三")
    private String highlightSnippet;

    /**
     * 相关性评分
     */
    @Schema(description = "相关性评分，分值越高越相关", example = "0.98")
    private Double score;

    /**
     * 状态：1启用 0禁用
     */
    @Schema(description = "资源状态：1-启用，0-禁用", example = "1")
    private Integer status;

    /**
     * 创建时间（格式化字符串）
     */
    @Schema(description = "创建时间，格式 yyyy-MM-dd HH:mm:ss", example = "2026-09-26 10:00:00")
    private String createdAt;

    /**
     * 更新时间（格式化字符串）
     */
    @Schema(description = "更新时间，格式 yyyy-MM-dd HH:mm:ss", example = "2026-09-26 15:30:00")
    private String updatedAt;
}
