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
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            throw new IllegalStateException("SQL 解析失败", e);
        }
        if (!(statement instanceof Select select)) {
            throw new IllegalStateException("数据权限仅支持 SELECT 结构");
        }
        Expression conditionExpr;
        try {
            conditionExpr = CCJSqlParserUtil.parseExpression(condition);
        } catch (Exception e) {
            throw new IllegalStateException("数据权限条件解析失败", e);
        }
        rewriteSelect(select, config, scope, conditionExpr, false);
        return select.toString();
    }

    private static void rewriteSelect(
            Select select, DataScopeConfig config, DataScopeInfo scope, Expression condition, boolean unionBranch) {
        Select body = select.getSelectBody();
        if (body instanceof PlainSelect plain) {
            String alias = scope.type() == com.apex.scaffold.common.enums.DataScopeType.SELF
                    ? config.userAlias()
                    : config.deptAlias();
            findTargetTable(plain, alias, unionBranch);
            if (plain.getWhere() == null) {
                plain.setWhere(condition);
            } else {
                plain.setWhere(new AndExpression(plain.getWhere(), condition));
            }
            return;
        }
        if (body instanceof SetOperationList setOperationList) {
            List<Select> selects = setOperationList.getSelects();
            if (selects == null || selects.isEmpty()) {
                throw new IllegalStateException("UNION 缺少 SELECT 分支");
            }
            for (Select branch : selects) {
                try {
                    rewriteSelect(branch, config, scope, condition, true);
                } catch (IllegalStateException e) {
                    throw new IllegalStateException("UNION 分支改写失败: " + e.getMessage(), e);
                }
            }
            return;
        }
        if (body instanceof ParenthesedSelect parenthesed) {
            rewriteSelect(parenthesed.getSelect(), config, scope, condition, unionBranch);
            return;
        }
        throw new IllegalStateException("不支持的 SELECT 结构");
    }

    /**
     * 定位过滤目标表。
     *
     * @param plain 主查询
     * @param alias 配置的部门表别名（可为空）
     * @return 目标表；未找到时返回 {@code null}
     */
    private static Table findTargetTable(PlainSelect plain, String alias, boolean unionBranch) {
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
            throw new IllegalStateException((unionBranch ? "UNION 分支" : "SELECT") + "未找到匹配别名: " + alias);
        }
        Table table = firstTable(plain.getFromItem());
        if (table == null) {
            throw new IllegalStateException("FROM 包含不支持的子查询");
        }
        return table;
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
