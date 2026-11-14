package com.apex.scaffold.tools.deployment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * information_schema 中单张表的列与索引快照。
 *
 * <p>列按 information_schema 返回顺序保存；复合索引按 {@code seq_in_index} 累积，
 * 索引行保留 {@code non_unique} 及其后的原始列，便于与 {@link V42Contract} 逐项比对。
 *
 * @author Apex Team
 * @since 1.3.1
 */
class SourceCatalog {

    /** 列名到列元数据的映射。 */
    private final LinkedHashMap<String, ColumnMeta> columns = new LinkedHashMap<>();

    /** 索引名到索引行集合的映射。 */
    private final LinkedHashMap<String, List<List<Object>>> indexes = new LinkedHashMap<>();

    /**
     * 返回列名集合。
     *
     * @return 列名集合
     */
    Set<String> columnNames() {
        return columns.keySet();
    }

    /**
     * 返回指定列元数据。
     *
     * @param name 列名
     * @return 列元数据；不存在时为 null
     */
    ColumnMeta column(String name) {
        return columns.get(name);
    }

    /**
     * 返回索引定义映射。
     *
     * @return 索引名到索引行集合的映射
     */
    LinkedHashMap<String, List<List<Object>>> indexes() {
        return indexes;
    }

    /**
     * 返回指定索引的索引行集合。
     *
     * @param name 索引名
     * @return 索引行集合；不存在时为 null
     */
    List<List<Object>> index(String name) {
        return indexes.get(name);
    }

    /**
     * 记录一列。
     *
     * @param column 列元数据
     */
    void addColumn(ColumnMeta column) {
        columns.put(column.name(), column);
    }

    /**
     * 追加一条索引行，索引名不存在时先创建集合。
     *
     * @param name 索引名
     * @param row  索引行
     */
    void addIndex(String name, List<Object> row) {
        indexes.computeIfAbsent(name, key -> new ArrayList<>()).add(row);
    }
}
