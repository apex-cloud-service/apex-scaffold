package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页响应结果。
 *
 * <p>通用分页数据传输对象，用于列表接口的统一分页返回。</p>
 *
 * <p>与 {@code apex-common} 分页契约的对齐口径（2026-11-07 模块评审 F8）：新增派生属性
 * {@code hasNext}（当前页码小于总页数），字段集与 common 版本一致；common 版本为兼容历史
 * 消费方额外暴露 {@code records} 访问器（与 {@code list} 同源的序列化别名），本版本不引入，
 * 避免同一列表在响应中重复出现。</p>
 *
 * @param <T> 列表元素类型
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页响应")
public class PageResponse<T> {

    /**
     * 当前页数据列表。
     *
     * <p>按分页参数截取的数据子集。</p>
     */
    @Schema(description = "数据列表")
    private List<T> list;

    /**
     * 总记录数。
     *
     * <p>不考虑分页的全量数据条数，用于前端计算总页数和显示总数。</p>
     */
    @Schema(description = "总记录数")
    private Long total;

    /**
     * 当前页码。
     *
     * <p>从 1 开始计数。</p>
     */
    @Schema(description = "当前页码")
    private Integer pageNum;

    /**
     * 每页大小。
     *
     * <p>单页返回的最大记录条数。</p>
     */
    @Schema(description = "每页大小")
    private Integer pageSize;

    /**
     * 总页数。
     *
     * <p>根据 {@code total} 和 {@code pageSize} 向上取整计算。</p>
     */
    @Schema(description = "总页数")
    private Integer pages;

    /**
     * 是否有下一页（派生属性）。
     *
     * <p>当前页码小于总页数时返回 true；页码或总页数缺失（如经无参构造或 builder 构建）
     * 时返回 false。不落独立字段，保证构造器与 builder 两条创建路径口径一致。</p>
     *
     * @return 是否存在下一页
     */
    @Schema(description = "是否有下一页")
    public Boolean getHasNext() {
        if (pageNum == null || pages == null) {
            return Boolean.FALSE;
        }
        return pageNum < pages;
    }

    /**
     * 构造分页响应结果。
     *
     * <p>自动根据总记录数和每页大小计算总页数；参数口径与 common 版本一致：
     * {@code pageSize} 为空或非正数抛 {@link IllegalArgumentException}，{@code total} 为空按 0 处理。</p>
     *
     * @param list     数据列表
     * @param total    总记录数
     * @param pageNum  当前页码
     * @param pageSize 每页大小
     * @throws IllegalArgumentException pageSize 为空或非正数
     */
    public PageResponse(List<T> list, Long total, Integer pageNum, Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        this.list = list;
        this.total = total == null ? 0L : total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = (int) Math.ceil((double) this.total / pageSize);
    }

    /**
     * 静态工厂方法。
     *
     * <p>便捷创建分页响应实例。</p>
     *
     * @param list     数据列表
     * @param total    总记录数
     * @param pageNum  当前页码
     * @param pageSize 每页大小
     * @param <T>      列表元素类型
     * @return 分页响应结果
     */
    public static <T> PageResponse<T> of(List<T> list, Long total, Integer pageNum, Integer pageSize) {
        return new PageResponse<>(list, total, pageNum, pageSize);
    }
}
