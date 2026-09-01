package com.apex.scaffold.infrastructure.persistence.util;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 分页工具类
 *
 * <p>提供分页查询的便捷方法
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class PageUtil {

    private static final Pattern ORDER_BY_PATTERN =
            Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)(?:\\s+(ASC|DESC))?$", Pattern.CASE_INSENSITIVE);

    /** 私有构造方法，防止实例化 */
    private PageUtil() {}

    /**
     * 开始分页
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param <E> 实体类型
     * @return Page对象
     */
    public static <E> Page<E> startPage(int pageNum, int pageSize) {
        return PageHelper.startPage(pageNum, pageSize);
    }

    /**
     * 开始分页（带排序）
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param orderBy 排序字段
     * @param <E> 实体类型
     * @return Page对象
     */
    public static <E> Page<E> startPage(int pageNum, int pageSize, String orderBy) {
        String normalizedOrderBy = normalizeOrderBy(orderBy);
        if (normalizedOrderBy == null) {
            return PageHelper.startPage(pageNum, pageSize);
        }
        return PageHelper.startPage(pageNum, pageSize, normalizedOrderBy);
    }

    /**
     * 分页查询
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param listQuery 查询方法
     * @param <E> 实体类型
     * @return 分页结果
     */
    public static <E> PageInfo<E> doPage(int pageNum, int pageSize, Supplier<List<E>> listQuery) {
        PageHelper.startPage(pageNum, pageSize);
        List<E> list = listQuery.get();
        return new PageInfo<>(list);
    }

    /**
     * 分页查询（带排序）
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param orderBy 排序字段
     * @param listQuery 查询方法
     * @param <E> 实体类型
     * @return 分页结果
     */
    public static <E> PageInfo<E> doPage(int pageNum, int pageSize, String orderBy, Supplier<List<E>> listQuery) {
        String normalizedOrderBy = normalizeOrderBy(orderBy);
        if (normalizedOrderBy == null) {
            PageHelper.startPage(pageNum, pageSize);
        } else {
            PageHelper.startPage(pageNum, pageSize, normalizedOrderBy);
        }
        List<E> list = listQuery.get();
        return new PageInfo<>(list);
    }

    /**
     * 规范化排序字段。
     *
     * <p>仅接受单个列名（可选 ASC/DESC 方向）的排序表达式，防止 SQL 注入。
     * 排序字段必须是字母、数字、下划线组成的合法列名，方向不区分大小写，
     * 输出统一为 {@code column ASC} / {@code column DESC} 格式。</p>
     *
     * @param orderBy 原始排序表达式，可为 null 或空白
     * @return 规范化后的排序表达式；输入为 null/空白时返回 null
     * @throws IllegalArgumentException 当排序表达式格式不合法时抛出
     */
    public static String normalizeOrderBy(String orderBy) {
        if (orderBy == null || orderBy.isBlank()) {
            return null;
        }
        String value = orderBy.trim();
        var matcher = ORDER_BY_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("排序字段格式不合法");
        }
        String column = matcher.group(1);
        String direction = matcher.group(2);
        return direction == null ? column : column + " " + direction.toLowerCase(Locale.ROOT);
    }

    /**
     * 偏移量分页
     *
     * @param offset 偏移量
     * @param limit 限制条数
     * @param <E> 实体类型
     * @return Page对象
     */
    public static <E> Page<E> offsetPage(int offset, int limit) {
        return PageHelper.offsetPage(offset, limit);
    }

    /**
     * 偏移量分页查询
     *
     * @param offset 偏移量
     * @param limit 限制条数
     * @param listQuery 查询方法
     * @param <E> 实体类型
     * @return 分页结果
     */
    public static <E> PageInfo<E> doOffsetPage(int offset, int limit, Supplier<List<E>> listQuery) {
        PageHelper.offsetPage(offset, limit);
        List<E> list = listQuery.get();
        return new PageInfo<>(list);
    }

    /** 清除分页参数 */
    public static void clearPage() {
        PageHelper.clearPage();
    }

    /**
     * 获取总记录数
     *
     * @param pageInfo 分页信息
     * @param <E> 实体类型
     * @return 总记录数
     */
    public static <E> long getTotal(PageInfo<E> pageInfo) {
        return pageInfo.getTotal();
    }

    /**
     * 获取总页数
     *
     * @param pageInfo 分页信息
     * @param <E> 实体类型
     * @return 总页数
     */
    public static <E> int getPages(PageInfo<E> pageInfo) {
        return pageInfo.getPages();
    }

    /**
     * 是否有下一页
     *
     * @param pageInfo 分页信息
     * @param <E> 实体类型
     * @return 是否有下一页
     */
    public static <E> boolean hasNextPage(PageInfo<E> pageInfo) {
        return pageInfo.isHasNextPage();
    }

    /**
     * 是否有上一页
     *
     * @param pageInfo 分页信息
     * @param <E> 实体类型
     * @return 是否有上一页
     */
    public static <E> boolean hasPreviousPage(PageInfo<E> pageInfo) {
        return pageInfo.isHasPreviousPage();
    }
}
