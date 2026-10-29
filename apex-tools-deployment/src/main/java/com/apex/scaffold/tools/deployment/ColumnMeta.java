package com.apex.scaffold.tools.deployment;

import java.util.List;

/**
 * information_schema.columns 中的单列元数据。
 *
 * <p>仅保留前置检查需要的属性，避免在比对逻辑中散落 JSON 数组下标。
 *
 * @author Apex Team
 * @since 1.3.1
 */
record ColumnMeta(
        String table,
        String name,
        String type,
        String nullable,
        String defaultValue,
        String extra,
        String generationExpression,
        String characterSet) {

    /**
     * 转换为与期望定义同构的 {@code [类型, 可空, 默认值, extra]}。
     *
     * @return 列定义
     */
    List<String> definition() {
        return V42Contract.field(type, nullable, defaultValue, extra);
    }
}
