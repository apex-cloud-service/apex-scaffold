package com.apex.scaffold.application.dto.data;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档响应 DTO。
 *
 * <p>用于接口层返回文档的完整信息，包括文档基本属性、内容、作者及统计数据等，避免领域实体直接暴露给前端。</p>
 *
 * <p>主要字段：</p>
 * <ul>
 *   <li>{@code id} - 文档主键 ID</li>
 *   <li>{@code title} - 文档标题</li>
 *   <li>{@code docType} - 文档类型（如 report、guide 等）</li>
 *   <li>{@code category} - 文档分类</li>
 *   <li>{@code content} - 文档正文内容</li>
 *   <li>{@code fileUrl} - 附件文件 URL</li>
 *   <li>{@code fileSize} - 附件文件大小（字节）</li>
 *   <li>{@code authorId}/{@code authorName} - 作者 ID 与姓名</li>
 *   <li>{@code status} - 状态（0-草稿，1-正常，2-归档）</li>
 *   <li>{@code tags} - 标签列表</li>
 *   <li>{@code viewCount}/{@code downloadCount} - 查看与下载次数</li>
 *   <li>{@code createTime}/{@code updateTime} - 创建与更新时间</li>
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
@Schema(description = "文档响应")
public class DocumentResponse {

    @Schema(description = "文档ID")
    private Long id;

    @Schema(description = "文档标题")
    private String title;

    @Schema(description = "文档类型")
    private String docType;

    @Schema(description = "文档分类")
    private String category;

    @Schema(description = "文档内容")
    private String content;

    @Schema(description = "文件URL")
    private String fileUrl;

    @Schema(description = "文件大小")
    private Long fileSize;

    @Schema(description = "作者ID")
    private Long authorId;

    @Schema(description = "作者姓名")
    private String authorName;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "标签")
    private List<String> tags;

    @Schema(description = "查看次数")
    private Integer viewCount;

    @Schema(description = "下载次数")
    private Integer downloadCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
