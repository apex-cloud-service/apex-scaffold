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
 * @version 1.3.2
 * @since 1.3.2
 */
class FlywayMigrationVersionTest {

    private static final Pattern VERSION_PATTERN = Pattern.compile("V([0-9_]+)__.+\\.sql");

    /**
     * 验证 MySQL 与 PostgreSQL 迁移版本集合一致，且版本号从 V1 连续递增到当前基线。
     *
     * @throws Exception 读取迁移目录失败时抛出
     */
    @Test
    void shouldHaveUniqueContiguousVersions() throws Exception {
        List<BigDecimal> mysqlVersions = migrationVersions("mysql");
        List<BigDecimal> postgresqlVersions = migrationVersions("postgresql");

        assertThat(mysqlVersions).isNotEmpty().contains(versionNumber("62"));
        assertThat(mysqlVersions)
                .as("MySQL and PostgreSQL migration versions must be identical")
                .containsExactlyElementsOf(postgresqlVersions);

        assertThat(mysqlVersions)
                .as("Migration versions must be contiguous starting from 1")
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, mysqlVersions.size())
                        .mapToObj(i -> versionNumber(String.valueOf(i)))
                        .toList());
    }

    /**
     * 读取指定数据库目录中的 Flyway 迁移版本。
     *
     * <p>Surefire 的工作目录可能是模块目录，也可能是仓库根；两种布局均被支持。
     *
     * @param database 数据库目录名（{@code mysql} 或 {@code postgresql}）
     * @return 按版本升序排列的迁移版本
     * @throws Exception 读取迁移目录失败时抛出
     */
    private List<BigDecimal> migrationVersions(String database) throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Path migrationDirectory = projectRoot.resolve("apex-system/src/main/resources/db/migration/" + database);
        if (!Files.isDirectory(migrationDirectory)) {
            migrationDirectory = projectRoot.resolve("src/main/resources/db/migration/" + database);
        }

        try (var files = Files.list(migrationDirectory)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(filename -> VERSION_PATTERN.matcher(filename).matches())
                    .map(this::version)
                    .sorted()
                    .toList();
        }
    }

    /**
     * 验证角色数据权限迁移显式创建角色-部门关系表及租户、审计字段。
     *
     * @throws Exception 读取迁移文件失败时抛出
     */
    @Test
    void shouldDefineExplicitRoleDataScopeTables() throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Path migration =
                projectRoot.resolve("apex-system/src/main/resources/db/migration/mysql/V18__add_role_data_scope.sql");
        if (!Files.exists(migration)) {
            migration = projectRoot.resolve("src/main/resources/db/migration/mysql/V18__add_role_data_scope.sql");
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

    /**
     * 验证部门迁移以组合租户键和外键强制部门与租户的一致性。
     *
     * @throws Exception 读取迁移文件失败时抛出
     */
    @Test
    void shouldEnforceDepartmentTenantIntegrity() throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Path migration = projectRoot.resolve(
                "apex-system/src/main/resources/db/migration/mysql/V19__enforce_department_tenant_integrity.sql");
        if (!Files.exists(migration)) {
            migration = projectRoot.resolve(
                    "src/main/resources/db/migration/mysql/V19__enforce_department_tenant_integrity.sql");
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

    /**
     * 验证租户隔离迁移覆盖既有关键业务表，且不调用错误的旧表名。
     *
     * @throws Exception 读取迁移文件失败时抛出
     */
    @Test
    void shouldCompleteTenantTableCoverage() throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Path migration = projectRoot.resolve(
                "apex-system/src/main/resources/db/migration/mysql/V15__complete_tenant_isolation_schema.sql");
        if (!Files.exists(migration)) {
            migration = projectRoot.resolve(
                    "src/main/resources/db/migration/mysql/V15__complete_tenant_isolation_schema.sql");
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
     * 验证 V90 仅回填内置 platform-full 套餐的空能力集合。
     *
     * <p>该检查是纯静态契约：不连接数据库、不依赖 Docker。它锁定两个数据库脚本
     * 都必须包含八项能力编码，并且回填条件只针对 {@code deleted = 0} 且 features
     * 为空数组或 NULL 的记录，避免迁移覆盖运营人员已经配置的非空能力集合。
     *
     * @throws Exception 读取迁移文件失败时抛出
     */
    @Test
    void shouldBackfillPlatformFullCapabilitiesWithoutOverwritingConfiguredFeatures() throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Path mysql = resolveMigration(projectRoot, "mysql", "V90__backfill_platform_full_capability_features.sql");
        Path postgresql =
                resolveMigration(projectRoot, "postgresql", "V90__backfill_platform_full_capability_features.sql");
        String mysqlSql = Files.readString(mysql);
        String postgresqlSql = Files.readString(postgresql);

        List<String> capabilities = List.of(
                "identity-auth",
                "user-org",
                "workflow",
                "file-storage",
                "office-message",
                "tenant-governance",
                "integration-connector",
                "iot-device");
        assertThat(mysqlSql).as("MySQL V90 必须回填全部八项能力").contains(capabilities);
        assertThat(postgresqlSql).as("PostgreSQL V90 必须回填全部八项能力").contains(capabilities);
        assertThat(mysqlSql)
                .as("MySQL V90 必须只回填 platform-full 的未删除记录")
                .contains(
                        "package_code` = 'platform-full'",
                        "`deleted` = 0",
                        "JSON_LENGTH(`features`) = 0",
                        "`features` IS NULL");
        assertThat(postgresqlSql)
                .as("PostgreSQL V90 必须只回填 platform-full 的未删除记录")
                .contains(
                        "package_code = 'platform-full'", "deleted = 0", "features = '[]'::jsonb", "features IS NULL");
        assertThat(mysqlSql)
                .as("MySQL V90 必须通过带条件的 UPDATE 回填，不得无条件覆盖 features")
                .contains("UPDATE `sys_tenant_package`")
                .contains("SET `features` = CAST(JSON_ARRAY(")
                .contains("WHERE `package_code` = 'platform-full'");
        assertThat(mysqlSql.indexOf("SET `features` = CAST(JSON_ARRAY("))
                .as("MySQL V90 的 SET 必须位于 WHERE 之前")
                .isGreaterThanOrEqualTo(0)
                .isLessThan(mysqlSql.indexOf("WHERE `package_code` = 'platform-full'"));
        assertThat(postgresqlSql)
                .as("PostgreSQL V90 必须通过带条件的 UPDATE 回填，不得无条件覆盖 features")
                .contains("UPDATE sys_tenant_package")
                .contains("SET features =")
                .contains("WHERE package_code = 'platform-full'");
        assertThat(postgresqlSql.indexOf("SET features ="))
                .as("PostgreSQL V90 的 SET 必须位于 WHERE 之前")
                .isGreaterThanOrEqualTo(0)
                .isLessThan(postgresqlSql.indexOf("WHERE package_code = 'platform-full'"));
    }

    /**
     * 验证 V78 的 platform-full 初始能力集合仍为空数组，V90 才是显式回填点。
     *
     * @throws Exception 读取迁移文件失败时抛出
     */
    @Test
    void shouldKeepV78EmptyFeatureInitialization() throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        String mysqlSql = Files.readString(resolveMigration(projectRoot, "mysql", "V78__create_tenant_package.sql"));
        String postgresqlSql =
                Files.readString(resolveMigration(projectRoot, "postgresql", "V78__create_tenant_package.sql"));

        assertThat(mysqlSql).contains("JSON_ARRAY()");
        assertThat(postgresqlSql).contains("'[]'::jsonb");
    }

    /**
     * 在两种可能的测试工作目录布局下解析迁移文件。
     *
     * @param projectRoot 当前测试工作目录
     * @param database 数据库目录名
     * @param filename 迁移文件名
     * @return 存在的迁移文件
     */
    private Path resolveMigration(Path projectRoot, String database, String filename) {
        Path fromRoot = projectRoot.resolve("apex-system/src/main/resources/db/migration/" + database + "/" + filename);
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }
        Path fromModule = projectRoot.resolve("src/main/resources/db/migration/" + database + "/" + filename);
        assertThat(fromModule).as("未找到迁移文件 %s/%s", database, filename).exists();
        return fromModule;
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
