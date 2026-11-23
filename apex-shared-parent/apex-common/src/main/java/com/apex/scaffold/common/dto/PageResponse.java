package com.apex.scaffold.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 分页响应类
 *
 * <p>统一分页响应格式
 *
 * @param <T> 数据类型
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(name = "PageResponse", description = "统一分页响应；list 为当前页数据，records 为其序列化别名")
public class PageResponse<T> implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 默认总记录数 */
    private static final long DEFAULT_TOTAL = 0L;
    /** 默认页码 */
    private static final int DEFAULT_PAGE_NUM = 1;

    /**
     * 数据列表
     *
     * <p>当前页的数据集合
     */
    @Schema(description = "当前页数据集合", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<T> list;

    public List<T> getRecords() {
        return list;
    }

    public void setRecords(List<T> records) {
        this.list = records;
    }

    /**
     * 总记录数
     *
     * <p>符合条件的所有记录数
     */
    @Schema(
            description = "符合条件的所有记录数",
            example = "128",
            requiredMode = Schema.RequiredMode.REQUIRED,
            type = "integer",
            format = "int64")
    private Long total;

    /**
     * 当前页码
     *
     * <p>从1开始
     */
    @Schema(
            description = "当前页码，从 1 开始",
            example = "1",
            minimum = "1",
            requiredMode = Schema.RequiredMode.REQUIRED,
            type = "integer",
            format = "int32")
    private Integer pageNum;

    /**
     * 每页大小
     *
     * <p>每页显示的记录数
     */
    @Schema(
            description = "每页显示的记录数",
            example = "20",
            minimum = "1",
            requiredMode = Schema.RequiredMode.REQUIRED,
            type = "integer",
            format = "int32")
    private Integer pageSize;

    /**
     * 总页数
     *
     * <p>计算公式：ceil(total / pageSize)
     */
    @Schema(
            description = "总页数，计算公式 ceil(total / pageSize)",
            example = "7",
            requiredMode = Schema.RequiredMode.REQUIRED,
            type = "integer",
            format = "int32")
    private Integer pages;

    /**
     * 是否有下一页
     *
     * <p>当前页码小于总页数时返回true
     */
    @Schema(description = "是否还有下一页；当前页码小于总页数时为 true", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean hasNext;

    /**
     * 创建分页响应
     *
     * <p>根据数据列表、总记录数、页码和每页大小创建分页响应对象
     *
     * @param list 数据列表
     * @param total 总记录数
     * @param pageNum 当前页码
     * @param pageSize 每页大小
     * @param <T> 数据类型
     * @return 分页响应
     * @throws IllegalArgumentException 如果 pageSize 为 null 或小于等于 0
     */
    public static <T> PageResponse<T> of(List<T> list, Long total, Integer pageNum, Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        long safeTotal = total == null ? DEFAULT_TOTAL : total;
        int safePageNum = pageNum == null ? DEFAULT_PAGE_NUM : pageNum;
        PageResponse<T> response = new PageResponse<>();
        response.setList(list == null ? java.util.Collections.emptyList() : list);
        response.setTotal(safeTotal);
        response.setPageNum(safePageNum);
        response.setPageSize(pageSize);

        int pages = (int) Math.ceil((double) safeTotal / pageSize);
        response.setPages(pages);
        response.setHasNext(safePageNum < pages);

        return response;
    }
}
