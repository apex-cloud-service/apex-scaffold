package com.apex.scaffold.common.dto;

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
    private List<T> list;

    /**
     * 总记录数
     *
     * <p>符合条件的所有记录数
     */
    private Long total;

    /**
     * 当前页码
     *
     * <p>从1开始
     */
    private Integer pageNum;

    /**
     * 每页大小
     *
     * <p>每页显示的记录数
     */
    private Integer pageSize;

    /**
     * 总页数
     *
     * <p>计算公式：ceil(total / pageSize)
     */
    private Integer pages;

    /**
     * 是否有下一页
     *
     * <p>当前页码小于总页数时返回true
     */
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
