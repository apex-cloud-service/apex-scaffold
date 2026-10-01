package com.apex.scaffold.domain.model.generator;

import lombok.Builder;
import lombok.Value;

/**
 * 表列信息值对象
 *
 * <p>封装数据库表列的元数据信息,用于代码生成时的类型映射和字段生成。
 * 该值对象是不可变的,通过Builder模式构造。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>描述列的物理属性:名称、数据类型、注释</li>
 *   <li>标识列的约束:是否可空、是否主键、是否自增</li>
 *   <li>为代码生成器提供类型映射的依据</li>
 * </ul>
 *
 * <h2>类型映射规则</h2>
 * <p>代码生成器根据 {@link #dataType} 映射为 Java 类型:
 * <ul>
 *   <li>VARCHAR/CHAR/TEXT → String</li>
 *   <li>INT/INTEGER/TINYINT → Integer (若自增则为 Long)</li>
 *   <li>BIGINT → Long</li>
 *   <li>DECIMAL/NUMERIC → BigDecimal</li>
 *   <li>DATE/DATETIME/TIMESTAMP → LocalDateTime</li>
 *   <li>BOOLEAN/BIT → Boolean</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * ColumnType column = ColumnType.builder()
 *     .columnName("user_id")
 *     .dataType("BIGINT")
 *     .columnComment("用户ID")
 *     .nullable(false)
 *     .isPrimaryKey(true)
 *     .autoIncrement(true)
 *     .build();
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Value
@Builder
public class ColumnType {

    /**
     * 列名（数据库物理列名）
     *
     * <p>示例：user_id, user_name, create_time
     *
     * <p>代码生成器会将其转换为驼峰命名的Java字段名。
     */
    String columnName;

    /**
     * 数据类型（数据库类型名称）
     *
     * <p>示例：VARCHAR, BIGINT, DATETIME, TEXT
     *
     * <p>不同数据库的类型名称可能存在差异,代码生成器需要统一映射规则。
     */
    String dataType;

    /**
     * 列注释（用于生成JavaDoc注释）
     *
     * <p>如果数据库列有注释,则提取为字段注释;否则使用默认注释或留空。
     */
    String columnComment;

    /**
     * 是否允许为空
     *
     * <p>true 表示列允许 NULL 值,生成的Java字段可能需要使用包装类型。
     *
     * <p>false 表示列有 NOT NULL 约束,生成的字段需要校验注解(如 @NotNull)。
     */
    boolean nullable;

    /**
     * 是否为主键
     *
     * <p>true 表示该列是表的主键或联合主键的一部分。
     *
     * <p>主键列通常会生成对应的查询方法(如 findById)。
     */
    boolean isPrimaryKey;

    /**
     * 是否自增列
     *
     * <p>true 表示该列是数据库自动递增的列(AUTO_INCREMENT、IDENTITY、SERIAL等)。
     *
     * <p>自增列在插入时不需要手动赋值,代码生成器会排除其在 INSERT 语句中的赋值逻辑。
     */
    boolean autoIncrement;
}
