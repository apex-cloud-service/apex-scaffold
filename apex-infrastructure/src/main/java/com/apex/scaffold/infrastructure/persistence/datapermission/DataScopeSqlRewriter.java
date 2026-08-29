package com.apex.scaffold.infrastructure.persistence.datapermission;

import com.apex.scaffold.common.context.DataScopeConfig;
import com.apex.scaffold.common.context.DataScopeInfo;
import java.util.List;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 数据权限 SQL 改写器（基于 JSQLParser 4.7，随 PageHelper 传递依赖提供，无新增依赖）。
 *
 * <p>将 {@link DataScopeConditionBuilder} 生成的条件片段注入 SELECT 查询：</p>
 *
 * <ul>
 *   <li>解析 SQL 并定位目标表：指定 {@code deptAlias} 时按别名匹配（含 JOIN 右表）；
 *       未指定时取主查询第一张表（MyBatis-Plus 自动生成的无别名 SQL 适用）；</li>
 *   <li>目标表为子查询（{@code SubSelect}）或结构无法识别时<strong>保守跳过</strong>，不做注入；</li>
 *   <li>已有 {@code WHERE} 时追加 {@code AND}，否则直接作为 {@code WHERE}；</li>
 *   <li>解析或改写失败时记录 WARN 并返回原 SQL（放行，避免阻断业务）。</li>
 * </ul>
 *
 * <p><b>注入列名规则</b>：配置了别名时使用 {@code alias.field}（多表 JOIN 防歧义）；
 * 未配置别名时使用裸列名 {@code field}（无别名单表场景）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 */
final class DataScopeSqlRewriter {

    private static final Logger log = LoggerFactory.getLogger(DataScopeSqlRewriter.class);

    /** 私有构造，禁止实例化 */
    private DataScopeSqlRewriter() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 将数据权限过滤条件注入 SQL。
     *
     * @param sql    原始 SQL（SELECT 语句）
     * @param config 注入配置
     * @param scope  数据权限信息
     * @return 注入后的 SQL；无需注入或无法改写时返回原 SQL
     */
    static String rewrite(String sql, DataScopeConfig config, DataScopeInfo scope) {
        String condition = DataScopeConditionBuilder.build(config, scope);
        if (condition == null || condition.isBlank()) {
            return sql;
        }
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select select)) {
                return sql;
            }
            PlainSelect plain = mainPlainSelect(select);
            if (plain == null) {
                return sql;
            }
            Table target = findTargetTable(plain, config.deptAlias());
            if (target == null) {
                log.debug("数据权限: 未找到目标表(alias={}), 跳过注入: {}", config.deptAlias(), sql);
                return sql;
            }
            Expression conditionExpr = CCJSqlParserUtil.parseExpression(condition);
            if (plain.getWhere() == null) {
                plain.setWhere(conditionExpr);
            } else {
                plain.setWhere(new AndExpression(plain.getWhere(), conditionExpr));
            }
            return select.toString();
        } catch (Exception e) {
            log.warn("数据权限: SQL 解析/改写失败, 跳过注入: {}", e.getMessage());
            return sql;
        }
    }

    /**
     * 提取主查询 {@link PlainSelect}。
     *
     * <p>JSQLParser 4.7 中 {@code PlainSelect}/{@code SetOperationList}/{@code ParenthesedSelect}
     * 均继承自 {@link Select}，{@code getSelectBody()} 返回的是子类型实例。
     * 支持普通 SELECT、UNION 集合操作（取第一个子查询）、括号包裹的子查询；
     * 其余结构返回 {@code null} 保守跳过。</p>
     */
    private static PlainSelect mainPlainSelect(Select select) {
        Select body = select.getSelectBody();
        if (body instanceof PlainSelect plain) {
            return plain;
        }
        if (body instanceof SetOperationList setOperationList) {
            List<Select> selects = setOperationList.getSelects();
            if (selects == null || selects.isEmpty()) {
                return null;
            }
            Select first = selects.get(0);
            return first instanceof PlainSelect plain ? plain : mainPlainSelect(first);
        }
        if (body instanceof ParenthesedSelect parenthesed) {
            return mainPlainSelect(parenthesed.getSelect());
        }
        return null;
    }

    /**
     * 定位过滤目标表。
     *
     * @param plain 主查询
     * @param alias 配置的部门表别名（可为空）
     * @return 目标表；未找到时返回 {@code null}
     */
    private static Table findTargetTable(PlainSelect plain, String alias) {
        if (alias != null && !alias.isBlank()) {
            Table matched = findTableByAlias(firstTable(plain.getFromItem()), alias);
            if (matched != null) {
                return matched;
            }
            if (plain.getJoins() != null) {
                for (Join join : plain.getJoins()) {
                    matched = findTableByAlias(firstTable(join.getRightItem()), alias);
                    if (matched != null) {
                        return matched;
                    }
                }
            }
            return null;
        }
        // 未指定别名 → 主查询第一张表
        return firstTable(plain.getFromItem());
    }

    /**
     * 提取 {@link FromItem} 中的第一张 {@link Table}。
     *
     * <p>{@link SubSelect} / 括号子查询等非表结构返回 {@code null}（保守跳过注入）。
     * JSQLParser 4.7 的 {@link Join} 只持有右侧 {@code rightItem}，
     * 左侧表由上层 {@code PlainSelect.fromItem} 持有。</p>
     */
    private static Table firstTable(FromItem item) {
        if (item instanceof Table table) {
            return table;
        }
        if (item instanceof Join join) {
            return firstTable(join.getRightItem());
        }
        return null;
    }

    /**
     * 按别名（忽略大小写）匹配表。
     */
    private static Table findTableByAlias(Table table, String alias) {
        if (table == null) {
            return null;
        }
        String actualAlias = table.getAlias() == null ? null : table.getAlias().getName();
        if (actualAlias == null) {
            return null;
        }
        return alias.equalsIgnoreCase(actualAlias) ? table : null;
    }
}
