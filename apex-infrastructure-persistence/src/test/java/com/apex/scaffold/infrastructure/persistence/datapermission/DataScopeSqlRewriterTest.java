package com.apex.scaffold.infrastructure.persistence.datapermission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.context.DataScopeConfig;
import com.apex.scaffold.common.context.DataScopeInfo;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 数据权限 SQL 改写器测试。
 *
 * <p>基于真实 JSQLParser 验证 {@link DataScopeSqlRewriter} 对各类 SELECT 的
 * 条件注入：无 WHERE / 有 WHERE / JOIN 别名 / 分组排序分页 / UNION / 子查询
 * / 解析失败等边界场景。
 *
 * @author Apex Team
 */
@DisplayName("DataScopeSqlRewriter 测试")
class DataScopeSqlRewriterTest {

    private static final DataScopeConfig DEFAULT_CONFIG = DataScopeConfig.defaults();

    @Nested
    @DisplayName("MyBatis-Plus 自动生成 SQL（无别名）")
    class MpGeneratedSqlTest {

        @Test
        void shouldInjectIntoExistingWhere() {
            String sql = "SELECT id, username, dept_id FROM sys_user WHERE deleted = 0";
            String rewritten = DataScopeSqlRewriter.rewrite(sql, DEFAULT_CONFIG, DataScopeInfo.ofDept(100L));

            assertThat(rewritten).isNotEqualTo(sql).contains("dept_id = 100").contains("deleted = 0");
            assertThat(rewritten).matches("(?s).*deleted = 0 AND dept_id = 100.*");
        }

        @Test
        void shouldAddWhereWhenAbsent() {
            String sql = "SELECT id, username, dept_id FROM sys_user";
            String rewritten = DataScopeSqlRewriter.rewrite(sql, DEFAULT_CONFIG, DataScopeInfo.ofDept(100L));

            assertThat(rewritten).isNotEqualTo(sql).contains("WHERE dept_id = 100");
        }

        @Test
        void shouldKeepOrderByAndLimitIntact() {
            String sql = "SELECT id, username FROM sys_user WHERE status = 1 ORDER BY id DESC LIMIT 10";
            String rewritten = DataScopeSqlRewriter.rewrite(sql, DEFAULT_CONFIG, DataScopeInfo.ofDept(100L));

            assertThat(rewritten)
                    .contains("ORDER BY id DESC")
                    .contains("LIMIT 10")
                    .contains("status = 1 AND dept_id = 100");
        }

        @Test
        void shouldKeepGroupByIntact() {
            String sql = "SELECT dept_id, COUNT(*) FROM sys_user WHERE status = 1 GROUP BY dept_id";
            String rewritten = DataScopeSqlRewriter.rewrite(sql, DEFAULT_CONFIG, DataScopeInfo.ofDept(100L));

            assertThat(rewritten).contains("GROUP BY dept_id").contains("status = 1 AND dept_id = 100");
        }
    }

    @Nested
    @DisplayName("多表 JOIN（指定别名）")
    class JoinAliasTest {

        @Test
        void shouldInjectConditionWithAlias() {
            String sql = "SELECT u.* FROM sys_user u LEFT JOIN sys_dept d ON u.dept_id = d.id WHERE u.status = 1";
            DataScopeConfig config = new DataScopeConfig("u", "u", "dept_id", "user_id");
            String rewritten = DataScopeSqlRewriter.rewrite(sql, config, DataScopeInfo.ofDept(100L));

            assertThat(rewritten)
                    .isNotEqualTo(sql)
                    .contains("u.dept_id = 100")
                    .contains("u.status = 1 AND u.dept_id = 100");
        }

        @Test
        void shouldMatchAliasIgnoringCase() {
            String sql = "SELECT U.* FROM sys_user U JOIN sys_dept D ON U.dept_id = D.id";
            DataScopeConfig config = new DataScopeConfig("u", "u", "dept_id", "user_id");
            String rewritten = DataScopeSqlRewriter.rewrite(sql, config, DataScopeInfo.ofDept(100L));

            assertThat(rewritten).isNotEqualTo(sql).contains("dept_id = 100");
        }

        @Test
        void shouldMatchAliasOnJoinRightTable() {
            String sql = "SELECT d.name FROM sys_user u JOIN sys_dept d ON u.dept_id = d.id";
            DataScopeConfig config = new DataScopeConfig("d", "", "dept_id", "user_id");
            String rewritten = DataScopeSqlRewriter.rewrite(sql, config, DataScopeInfo.ofDept(100L));

            assertThat(rewritten).contains("d.dept_id = 100");
        }

        @Test
        void shouldRejectWhenAliasNotFound() {
            String sql = "SELECT u.* FROM sys_user u LEFT JOIN sys_dept d ON u.dept_id = d.id";
            DataScopeConfig config = new DataScopeConfig("x", "x", "dept_id", "user_id");

            assertThatThrownBy(() -> DataScopeSqlRewriter.rewrite(sql, config, DataScopeInfo.ofDept(100L)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("别名")
                    .hasMessageContaining("x");
        }
    }

    @Nested
    @DisplayName("数据权限类型注入")
    class ScopeTypeTest {

        @Test
        void shouldInjectDeptAndChildInClause() {
            String sql = "SELECT * FROM sys_user";
            String rewritten = DataScopeSqlRewriter.rewrite(
                    sql, DEFAULT_CONFIG, DataScopeInfo.ofDeptAndChild(100L, List.of(100L, 101L, 102L)));

            assertThat(rewritten).contains("dept_id IN (100, 101, 102)");
        }

        @Test
        void shouldInjectSelfCondition() {
            String sql = "SELECT * FROM sys_user";
            String rewritten = DataScopeSqlRewriter.rewrite(sql, DEFAULT_CONFIG, DataScopeInfo.ofSelf(42L));

            assertThat(rewritten).contains("user_id = 42");
        }

        @Test
        void shouldInjectCustomInClause() {
            String sql = "SELECT * FROM sys_user";
            String rewritten =
                    DataScopeSqlRewriter.rewrite(sql, DEFAULT_CONFIG, DataScopeInfo.ofCustom(List.of(1L, 2L)));

            assertThat(rewritten).contains("dept_id IN (1, 2)");
        }

        @Test
        void shouldLeaveSqlUntouchedForAllScope() {
            String sql = "SELECT * FROM sys_user WHERE status = 1";
            assertThat(DataScopeSqlRewriter.rewrite(sql, DEFAULT_CONFIG, DataScopeInfo.all()))
                    .isEqualTo(sql);
        }
    }

    @Nested
    @DisplayName("复杂结构与失败降级")
    class ComplexAndFailureTest {

        @Test
        void shouldInjectIntoFirstSelectOfUnion() {
            String sql =
                    "SELECT id, dept_id FROM sys_user WHERE deleted = 0 UNION SELECT id, dept_id FROM sys_user_archive";
            String rewritten = DataScopeSqlRewriter.rewrite(sql, DEFAULT_CONFIG, DataScopeInfo.ofDept(100L));

            assertThat(rewritten).isNotEqualTo(sql);
            assertThat(rewritten.split("dept_id = 100", -1)).hasSize(3);
        }

        @Test
        void shouldRejectSubqueryFrom() {
            String sql = "SELECT * FROM (SELECT id, dept_id FROM sys_user) t WHERE t.id > 1";

            assertThatThrownBy(() -> DataScopeSqlRewriter.rewrite(sql, DEFAULT_CONFIG, DataScopeInfo.ofDept(100L)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FROM")
                    .hasMessageContaining("子查询");
        }

        @Test
        void shouldRejectNonSelect() {
            String sql = "UPDATE sys_user SET status = 1 WHERE id = 1";

            assertThatThrownBy(() -> DataScopeSqlRewriter.rewrite(sql, DEFAULT_CONFIG, DataScopeInfo.ofDept(100L)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SELECT");
        }

        @Test
        void shouldRejectUnparseableSql() {
            String sql = "SELEC * FRM sys_user WHERE";

            assertThatThrownBy(() -> DataScopeSqlRewriter.rewrite(sql, DEFAULT_CONFIG, DataScopeInfo.ofDept(100L)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SQL")
                    .hasMessageContaining("解析");
        }

        @Test
        void shouldRejectUnsupportedSelectStructure() {
            String sql = "VALUES (1)";

            assertThatThrownBy(() -> DataScopeSqlRewriter.rewrite(sql, DEFAULT_CONFIG, DataScopeInfo.ofDept(100L)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SELECT")
                    .hasMessageContaining("结构");
        }

        @Test
        void shouldKeepConditionWhenWhereHasParentheses() {
            String sql = "SELECT * FROM sys_user WHERE (status = 1 OR status = 2) AND deleted = 0";
            String rewritten = DataScopeSqlRewriter.rewrite(sql, DEFAULT_CONFIG, DataScopeInfo.ofDept(100L));

            assertThat(rewritten).contains("dept_id = 100");
            assertThat(rewritten).contains("(status = 1 OR status = 2)");
        }
    }
}
