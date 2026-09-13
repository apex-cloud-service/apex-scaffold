package com.apex.scaffold.application.dto.data;

import com.apex.scaffold.application.dto.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 文档查询请求 DTO。
 *
 * <p>用于文档列表的分页与条件查询,继承 {@link PageRequest} 的分页参数,支持按关键词、文档类型、分类和状态过滤。</p>
 *
 * <p>主要字段:</p>
 * <ul>
 *   <li>{@code keyword} - 关键词,匹配文档标题或内容</li>
 *   <li>{@code docType} - 文档类型过滤</li>
 *   <li>{@code category} - 文档分类过滤</li>
 *   <li>{@code status} - 状态过滤(0-草稿,1-正常,2-归档)</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Schema(description = "文档查询请求")
/**n * DocumentQueryRequestn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class DocumentQueryRequest extends PageRequest {

    @Schema(description = "关键词(标题/内容)")
    private String keyword;

    @Schema(description = "文档类型")
    private String docType;

    @Schema(description = "文档分类")
    private String category;

    @Schema(description = "状态")
    private Integer status;
}
