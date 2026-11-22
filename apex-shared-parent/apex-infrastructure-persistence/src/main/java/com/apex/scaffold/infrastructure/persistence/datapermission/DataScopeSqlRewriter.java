package com.apex.scaffold.infrastructure.persistence.datapermission;

import com.apex.scaffold.common.context.DataScopeConfig;
import com.apex.scaffold.common.context.DataScopeInfo;
import java.util.List;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
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
 *   <li>仅在无需过滤时返回原 SQL；需要过滤时解析 SELECT 并校验目标表；</li>
 *   <li>SELF 使用 {@code userAlias}，其他过滤类型使用 {@code deptAlias}，
 *       按别名匹配 FROM 表或 JOIN 右表；未指定别名时使用 FROM 表；</li>
 *   <li>递归处理集合操作的各个 SELECT 分支及括号包裹的 SELECT；
 *       已有 {@code WHERE} 时追加 {@code AND}，根节点为 {@code OR} 时先整体括起原条件；</li>
 *   <li>采用 fail-closed 策略：权限关键值缺失、SQL 或条件解析失败、目标别名未匹配、
 *       目标表无法识别或 SELECT 结构不受支持时抛出 {@link IllegalStateException}，阻止查询。</li>
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

    /**
     * 私有构造，禁止实例化。
     *
     * @throws UnsupportedOperationException 调用时始终抛出
     */
    private DataScopeSqlRewriter() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 将数据权限过滤条件注入 SQL。
     *
     * @param sql    原始 SQL（SELECT 语句）
     * @param config 注入配置
     * @param scope  数据权限信息
     * @return 注入后的 SQL；无需过滤时返回原 SQL
     * @throws IllegalStateException 权限关键值缺失、SQL 或条件解析失败、非 SELECT 语句、
     *     目标表校验失败或 SELECT 结构不受支持时抛出
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

    /**
     * 递归校验 SELECT 目标表并注入过滤条件，原地修改解析树。
     *
     * @param select 待改写的 SELECT
     * @param config 注入配置
     * @param scope 数据权限信息，用于选择用户或部门别名
     * @param condition 已解析的过滤条件
     * @param unionBranch 是否位于集合操作分支，用于异常消息
     * @throws IllegalStateException 集合操作缺少分支、目标表校验失败或 SELECT 结构不受支持时抛出
     */
    private static void rewriteSelect(
            Select select, DataScopeConfig config, DataScopeInfo scope, Expression condition, boolean unionBranch) {
        Select body = select.getSelectBody();
        if (body instanceof PlainSelect plain) {
            String alias = scope.type() == com.apex.scaffold.common.enums.DataScopeType.SELF
                    ? config.userAlias()
                    : config.deptAlias();
            findTargetTable(plain, alias, unionBranch);
            Expression where = plain.getWhere();
            if (where == null) {
                plain.setWhere(condition);
            } else if (needsGrouping(where)) {
                // 根节点为 OR 时，原条件必须整体括起：否则 a OR b AND scope 会按
                // a OR (b AND scope) 解析，导致数据权限被绕过。Parenthesis 需已有元素，
                // 故先按字符串解析括号表达式再追加。
                Expression grouped;
                try {
                    grouped = CCJSqlParserUtil.parseCondExpression("(" + where + ")");
                } catch (Exception e) {
                    throw new IllegalStateException("数据权限条件合并失败", e);
                }
                plain.setWhere(new AndExpression(grouped, condition));
            } else {
                plain.setWhere(new AndExpression(where, condition));
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
     * 判断追加 {@code AND} 前是否必须把原条件整体括起。
     *
     * <p>{@code AND} 优先级高于 {@code OR}，因此只有根节点是 {@link OrExpression} 时
     * 直接追加会改变语义；子节点中的 {@code OR} 在原始 SQL 中必然已带括号，
     * 无需重复处理。
     *
     * @param where 原 WHERE 条件
     * @return 需要括号时为 {@code true}
     */
    private static boolean needsGrouping(Expression where) {
        return where instanceof OrExpression;
    }

    /**
     * 定位过滤目标表。
     *
     * @param plain 当前 SELECT 查询
     * @param alias 配置的用户表或部门表别名（可为空或空白）
     * @param unionBranch 是否位于集合操作分支，用于异常消息
     * @return 匹配别名的目标表；未配置别名时返回 FROM 表
     * @throws IllegalStateException 配置的别名未匹配到表，或未配置别名且 FROM 不是可识别的表时抛出
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
     * <p>括号子查询等非表结构返回 {@code null}，由调用方继续查找或抛出异常。
     * {@link Join} 只提取右侧 {@code rightItem}，左侧表由上层
     * {@code PlainSelect.fromItem} 持有。</p>
     *
     * @param item FROM 项或 JOIN 右项
     * @return 可识别的表；非表结构或空值返回 {@code null}
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
     *
     * @param table 待匹配的表，可为空
     * @param alias 期望的表别名
     * @return 匹配的表；表为空、无别名或别名不匹配时返回 {@code null}
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
