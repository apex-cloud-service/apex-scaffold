package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 无需 Docker 的 Flyway 版本链静态检查。
 *
 * <p>迁移版本必须从 V1 连续递增，当前基线包含租户字段迁移。</p>
 *
 * @author Apex Team
 */
class FlywayMigrationVersionTest {

    private static final Pattern VERSION_PATTERN = Pattern.compile("V([0-9_]+)__.+\\.sql");

    /**
     * 执行 {@code shouldHaveUniqueContiguousVersions} 操作。
     * @throws Exception 当操作无法完成时
     */
    @Test
    void shouldHaveUniqueContiguousVersions() throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Path migrationDirectory = projectRoot.resolve("apex-interfaces/src/main/resources/db/migration");
        if (!Files.isDirectory(migrationDirectory)) {
            migrationDirectory = projectRoot.resolve("src/main/resources/db/migration");
        }

        List<BigDecimal> versions;
        try (var files = Files.list(migrationDirectory)) {
            versions = files.map(path -> path.getFileName().toString())
                    .filter(filename -> VERSION_PATTERN.matcher(filename).matches())
                    .map(this::version)
                    .sorted()
                    .toList();
        }

        assertThat(versions)
                .containsExactlyElementsOf(List.of(
                        versionNumber("1"),
                        versionNumber("2"),
                        versionNumber("3"),
                        versionNumber("4"),
                        versionNumber("5"),
                        versionNumber("6"),
                        versionNumber("7"),
                        versionNumber("8"),
                        versionNumber("9"),
                        versionNumber("10"),
                        versionNumber("11"),
                        versionNumber("12"),
                        versionNumber("13"),
                        versionNumber("14"),
                        versionNumber("15"),
                        versionNumber("16"),
                        versionNumber("17"),
                        versionNumber("18"),
                        versionNumber("19")));
    }

    @Test
    void shouldDefineExplicitRoleDataScopeTables() throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Path migration =
                projectRoot.resolve("apex-interfaces/src/main/resources/db/migration/V18__add_role_data_scope.sql");
        if (!Files.exists(migration)) {
            migration = projectRoot.resolve("src/main/resources/db/migration/V18__add_role_data_scope.sql");
        }

        String sql = Files.readString(migration);
        assertThat(sql)
                .contains(
                        "ALTER TABLE `sys_role`",
                        "`data_scope`",
                        "CREATE TABLE IF NOT EXISTS `sys_role_dept`",
                        "`tenant_id`",
                        "`role_id`",
                        "`dept_id`",
                        "`deleted`",
                        "`version`");
        assertThat(sql)
                .contains("idx_sys_role_dept_tenant_id", "idx_sys_role_dept_role_id", "idx_sys_role_dept_dept_id");
    }

    @Test
    void shouldEnforceDepartmentTenantIntegrity() throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Path migration = projectRoot.resolve(
                "apex-interfaces/src/main/resources/db/migration/V19__enforce_department_tenant_integrity.sql");
        if (!Files.exists(migration)) {
            migration =
                    projectRoot.resolve("src/main/resources/db/migration/V19__enforce_department_tenant_integrity.sql");
        }

        String sql = Files.readString(migration);
        assertThat(sql)
                .contains(
                        "SIGNAL SQLSTATE '45000'",
                        "`open_id` = 'admin-openid'",
                        "`username` = 'admin'",
                        "`name` = 'Apex 科技'",
                        "`dept_id` = 1",
                        "MODIFY COLUMN `tenant_id` VARCHAR(64) NOT NULL",
                        "UNIQUE KEY `uk_sys_dept_tenant_id_id` (`tenant_id`, `id`)",
                        "FOREIGN KEY (`tenant_id`, `dept_id`)",
                        "REFERENCES `sys_dept` (`tenant_id`, `id`)");
    }

    @Test
    void shouldCompleteTenantTableCoverage() throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Path migration = projectRoot.resolve(
                "apex-interfaces/src/main/resources/db/migration/V15__complete_tenant_isolation_schema.sql");
        if (!Files.exists(migration)) {
            migration =
                    projectRoot.resolve("src/main/resources/db/migration/V15__complete_tenant_isolation_schema.sql");
        }

        String sql = Files.readString(migration);
        assertThat(sql)
                .contains(
                        "sys_operation_log",
                        "biz_system_message",
                        "biz_credit_tx",
                        "sys_file",
                        "sys_login_log",
                        "biz_admin_review_audit");
        assertThat(sql)
                .doesNotContain(
                        "CALL add_tenant_id_if_not_exists('operation_log'",
                        "CALL add_tenant_id_if_not_exists('system_message'");
    }

    /**
     * 从迁移文件名提取可排序的 Flyway 版本。
     *
     * @param filename 迁移文件名
     * @return 规范化版本号
     */
    private BigDecimal version(String filename) {
        Matcher matcher = VERSION_PATTERN.matcher(filename == null ? "" : filename);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("非法 Flyway 文件名: " + filename);
        }
        return versionNumber(matcher.group(1).replace('_', '.'));
    }

    /**
     * 构造用于精确比较的版本号。
     *
     * @param value 点分版本文本
     * @return 十进制版本值
     */
    private BigDecimal versionNumber(String value) {
        return new BigDecimal(value);
    }
}
