package com.apex.scaffold.tools.deployment;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * 单张契约表：字段定义、索引定义与重建 DDL。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public class SourceTable {

    /** 字段名到 [类型, 可空, 默认值, extra] 的映射。 */
    private final LinkedHashMap<String, List<String>> fields = new LinkedHashMap<>();

    /** 索引名到索引列行的映射。 */
    private final LinkedHashMap<String, List<List<Object>>> indexes = new LinkedHashMap<>();

    /** 重建该表的 DDL。 */
    private final String ddl;

    /**
     * 构造契约表。
     *
     * @param ddl 重建该表的 DDL
     */
    public SourceTable(String ddl) {
        this.ddl = ddl;
    }

    /**
     * 返回字段定义映射。
     *
     * @return 字段定义映射
     */
    public LinkedHashMap<String, List<String>> getFields() {
        return fields;
    }

    /**
     * 返回索引定义映射。
     *
     * @return 索引定义映射
     */
    public LinkedHashMap<String, List<List<Object>>> getIndexes() {
        return indexes;
    }

    /**
     * 返回重建 DDL。
     *
     * @return 建表语句
     */
    public String getDdl() {
        return ddl;
    }
}
