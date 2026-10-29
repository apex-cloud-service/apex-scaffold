package com.apex.scaffold.tools.deployment;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * 由原始 V42 迁移 SQL 解析出的结构契约：新增列、三张契约表与健康视图定义。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public class SourceContract {

    /** ADD COLUMN 子句原文。 */
    private final List<String> columns;

    /** 表名到契约表的映射，保持 SQL 中出现顺序。 */
    private final LinkedHashMap<String, SourceTable> tables;

    /** 规范化后的视图定义。 */
    private final String view;

    /**
     * 构造结构契约。
     *
     * @param columns 新增列子句
     * @param tables  契约表
     * @param view    视图定义
     */
    public SourceContract(List<String> columns, LinkedHashMap<String, SourceTable> tables, String view) {
        this.columns = columns;
        this.tables = tables;
        this.view = view;
    }

    /**
     * 返回新增列子句。
     *
     * @return 新增列子句列表
     */
    public List<String> getColumns() {
        return columns;
    }

    /**
     * 返回契约表映射。
     *
     * @return 表名到契约表的映射
     */
    public LinkedHashMap<String, SourceTable> getTables() {
        return tables;
    }

    /**
     * 返回视图定义。
     *
     * @return 规范化视图定义
     */
    public String getView() {
        return view;
    }
}
