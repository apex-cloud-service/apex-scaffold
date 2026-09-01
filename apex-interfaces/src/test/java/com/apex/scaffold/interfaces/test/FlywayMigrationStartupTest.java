package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;

/**
 * 集成测试启动校验：MySQL 迁移链完整性。
 *
 * <p>it profile 使用真实 MySQL（dynamic-datasource + Druid，与 prod 同构），
 * Spring 启动时 Flyway 自动执行 {@code classpath:db/migration}（MySQL 版 V1-V15）。
 * 本测试断言：迁移全部应用（flyway_schema_history 15 条）、关键表与种子就位，
 * 防止「迁移 SQL 坏导致应用无法启动」回归（历史教训：V4/V6 sys_menu 列名错误）。</p>
 *
 * @author Apex Team
 */
@SpringBootTest
@ActiveProfiles("it")
@Import(ItProfileMockRedisConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FlywayMigrationStartupTest {

    private final DataSource dataSource;

    /**
     * 构造器注入 DataSource。
     *
     * @param dataSource 数据源
     */
    public FlywayMigrationStartupTest(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 执行 {@code shouldStartWithEmbeddedDependencies} 操作。
     *
     * @throws SQLException 当操作无法完成时
     */
    @Test
    void shouldStartWithEmbeddedDependencies() throws SQLException {
        try (var connection = dataSource.getConnection();
                var rs = connection.getMetaData().getTables(null, null, "sys_user", null)) {
            assertThat(rs.next()).isTrue();
        }
    }

    /**
     * 执行 {@code shouldApplyAllSixMigrations} 操作。
     *
     * <p>Flyway 迁移链 V1-V15 必须全部应用成功（MySQL 版，各环境共用同一套迁移）。
     * 新增迁移脚本时同步更新本断言数量。</p>
     *
     * @throws SQLException 当操作无法完成时
     */
    @Test
    void shouldApplyAllMigrationsThroughV19() throws SQLException {
        try (var connection = dataSource.getConnection();
                var st = connection.createStatement();
                var rs = st.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("V1-V19 十九个迁移应全部应用成功").isEqualTo(19);
        }
    }

    @Test
    void shouldHaveRoleDataScopeSchema() throws SQLException {
        try (var connection = dataSource.getConnection();
                var st = connection.createStatement();
                var rs = st.executeQuery("SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND "
                        + "((table_name = 'sys_role' AND column_name = 'data_scope') "
                        + "OR (table_name = 'sys_dept' AND column_name = 'tenant_id' "
                        + "AND is_nullable = 'NO'))")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
        try (var connection = dataSource.getConnection();
                var st = connection.createStatement();
                var rs = st.executeQuery("SELECT COUNT(*) FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = DATABASE() "
                        + "AND constraint_name = 'fk_sys_role_dept_tenant_dept'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void shouldRejectCrossTenantRoleDepartmentRelation() throws SQLException {
        try (var connection = dataSource.getConnection();
                var st = connection.createStatement()) {
            st.executeUpdate("INSERT INTO sys_dept "
                    + "(id, parent_id, ancestors, name, tenant_id) "
                    + "VALUES (91001, 0, '0', 'tenant-a-dept', 'tenant-a')");
            try {
                st.executeUpdate("INSERT INTO sys_role_dept (id, role_id, dept_id, tenant_id) "
                        + "VALUES (91001, 1, 91001, 'tenant-a')");
                assertThatThrownBy(() -> st.executeUpdate("INSERT INTO sys_role_dept (id, role_id, dept_id, tenant_id) "
                                + "VALUES (91002, 1, 91001, 'tenant-b')"))
                        .isInstanceOf(SQLException.class);
            } finally {
                st.executeUpdate("DELETE FROM sys_role_dept WHERE id IN (91001, 91002)");
                st.executeUpdate("DELETE FROM sys_dept WHERE id = 91001");
            }
        }
    }

    /**
     * 执行 {@code shouldHaveAuditAndDeptSeeds} 操作。
     *
     * <p>校验 V4（审计日志菜单）与 V6（部门管理菜单）修复后的种子数据可正常写入。</p>
     *
     * @throws SQLException 当操作无法完成时
     */
    @Test
    void shouldHaveAuditAndDeptSeeds() throws SQLException {
        try (var connection = dataSource.getConnection();
                var st = connection.createStatement();
                var rs = st.executeQuery("SELECT COUNT(*) FROM sys_menu WHERE menu_name IN ('审计日志','部门管理')")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("V4/V6 菜单种子").isEqualTo(2);
        }
        try (var connection = dataSource.getConnection();
                var st = connection.createStatement();
                var rs = st.executeQuery("SELECT COUNT(*) FROM sys_dept WHERE id = 1 AND name = 'Apex 科技'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("V19 应移除未归属租户的 V6 演示部门").isZero();
        }
        try (var connection = dataSource.getConnection();
                var st = connection.createStatement();
                var rs = st.executeQuery("SELECT COUNT(*) FROM sys_user WHERE dept_id = 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("V19 不应留下对已删除演示部门的用户引用").isZero();
        }
    }

    /**
     * 执行 {@code shouldHaveV3AndV6UserColumns} 操作。
     *
     * @throws SQLException 当操作无法完成时
     */
    @Test
    void shouldHaveV3AndV6UserColumns() throws SQLException {
        try (var connection = dataSource.getConnection();
                var st = connection.createStatement();
                var rs = st.executeQuery("SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'sys_user' "
                        + "AND column_name IN ('username','password','dept_id')")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("V3 username/password + V6 dept_id 应就位").isEqualTo(3);
        }
    }
}
