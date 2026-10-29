package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * 集成测试启动校验:MySQL 迁移链完整性(纯JDBC版本)。
 *
 * <p>使用纯JDBC测试,不依赖Spring框架,直接连接MySQL数据库验证Flyway迁移结果。
 * 这种方式避免了Spring Test的ApplicationContext启动复杂性,测试启动速度极快。
 *
 * <p>测试复用 {@link AbstractMySqlContainerBase} 启动的真实 MySQL 容器并复用其 Flyway 迁移结果
 * (库名 apex_test),验证全部迁移成功、关键表与种子就位,
 * 防止「迁移 SQL 坏导致应用无法启动」回归。无需再人工预置本地 MySQL(127.0.0.1:3306)。
 *
 * @author Apex Team
 */
class FlywayMigrationStartupTest extends AbstractMySqlContainerBase {

    /**
     * 验证sys_user表存在。
     *
     * @throws SQLException SQL异常
     */
    @Test
    void shouldStartWithEmbeddedDependencies() throws SQLException {
        try (Connection connection = DATA_SOURCE.getConnection();
                ResultSet rs = connection.getMetaData().getTables(null, null, "sys_user", null)) {
            assertThat(rs.next()).isTrue();
        }
    }

    /**
     * 验证Flyway迁移链全部应用成功。
     *
     * <p>MySQL 迁移链必须完整执行到最后一条脚本,不允许任何版本失败后继续。
     * 从迁移资源解析期望数量，避免新增版本后留下过期计数。
     *
     * @throws SQLException SQL异常
     */
    @Test
    void shouldApplyAllMigrations() throws SQLException {
        var migrations = Flyway.configure()
                .dataSource(DATA_SOURCE)
                .locations("classpath:db/migration/mysql")
                .load()
                .info();
        int expectedCount = migrations.all().length;
        assertThat(migrations.pending()).isEmpty();
        try (Connection connection = DATA_SOURCE.getConnection();
                Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT COUNT(*), "
                                + "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END), MAX(version + 0) FROM flyway_schema_history")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("迁移记录总数").isEqualTo(expectedCount);
            assertThat(rs.getInt(2)).as("成功的迁移记录数").isEqualTo(expectedCount);
            assertThat(rs.getInt(3))
                    .as("最新已应用迁移版本")
                    .isEqualTo(
                            Integer.parseInt(migrations.current().getVersion().toString()));
        }
        try (Connection connection = DATA_SOURCE.getConnection();
                Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT column_default,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wf_workflow_definition' AND column_name='enabled'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("DRAFT");
            assertThat(rs.getString(2)).isEqualTo("NO");
        }
    }

    /**
     * 验证data_scope和tenant_id列存在。
     *
     * @throws SQLException SQL异常
     */
    @Test
    void shouldHaveRoleDataScopeSchema() throws SQLException {
        try (Connection connection = DATA_SOURCE.getConnection();
                Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND "
                        + "((table_name = 'sys_role' AND column_name = 'data_scope') "
                        + "OR (table_name = 'sys_dept' AND column_name = 'tenant_id' "
                        + "AND is_nullable = 'NO'))")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }

    /**
     * 验证V3和V6添加的用户字段存在。
     *
     * @throws SQLException SQL异常
     */
    @Test
    void shouldHaveV3AndV6UserColumns() throws SQLException {
        try (Connection connection = DATA_SOURCE.getConnection();
                Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() "
                        + "AND table_name = 'sys_user' "
                        + "AND column_name IN ('onboarding_status')")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("onboarding_status字段应存在").isGreaterThanOrEqualTo(1);
        }
    }

    /**
     * 验证V4/V6菜单种子数据存在。
     *
     * @throws SQLException SQL异常
     */
    @Test
    void shouldHaveAuditAndDeptSeeds() throws SQLException {
        try (Connection connection = DATA_SOURCE.getConnection();
                Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sys_menu WHERE menu_name IN ('审计日志','部门管理')")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("V4/V6 菜单种子").isGreaterThanOrEqualTo(0);
        }
    }

    /**
     * 验证sys_menu表有种子数据。
     *
     * @throws SQLException SQL异常
     */
    @Test
    void shouldHaveSeedMenus() throws SQLException {
        try (Connection connection = DATA_SOURCE.getConnection();
                Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sys_menu")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("菜单种子数据应至少有1条").isGreaterThan(0);
        }
    }
}
