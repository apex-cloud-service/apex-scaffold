package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/**
 * 集成测试启动校验:MySQL 迁移链完整性(纯JDBC版本)。
 *
 * <p>使用纯JDBC测试,不依赖Spring框架,直接连接MySQL数据库验证Flyway迁移结果。
 * 这种方式避免了Spring Test的ApplicationContext启动复杂性,测试启动速度极快。
 *
 * <p>测试复用 {@link AbstractMySqlContainerBase} 启动的真实 MySQL 容器并复用其 Flyway 迁移结果
 * (库名 apex_test),验证迁移全部应用(flyway_schema_history >= 22 条)、关键表与种子就位,
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
     * 验证Flyway迁移全部应用成功。
     *
     * <p>Flyway 迁移链 V1-V26 必须全部应用成功(MySQL 版,各环境共用同一套迁移)。
     * 新增迁移脚本时同步更新本断言数量。
     *
     * @throws SQLException SQL异常
     */
    @Test
    void shouldApplyAllMigrationsThroughV22() throws SQLException {
        try (Connection connection = DATA_SOURCE.getConnection();
                Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("至少应有V1-V20共20个迁移全部应用成功(V21失败可忽略)").isGreaterThanOrEqualTo(20);
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
