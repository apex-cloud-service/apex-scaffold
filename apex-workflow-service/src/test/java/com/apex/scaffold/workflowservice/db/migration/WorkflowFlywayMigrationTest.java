package com.apex.scaffold.workflowservice.db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.workflow.infrastructure.persistence.po.ApprovalRecordPO;
import com.apex.scaffold.workflow.infrastructure.persistence.po.ProcessInstancePO;
import com.apex.scaffold.workflow.infrastructure.persistence.po.TaskPO;
import com.apex.scaffold.workflow.infrastructure.persistence.po.WorkflowDefinitionPO;
import com.baomidou.mybatisplus.annotation.TableField;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * 无需 Docker 的 Flyway 迁移脚本静态结构校验。
 *
 * <p>工作流服务（apex-workflow-service）使用独立库 {@code apex_workflow}，迁移版本链与旧单体
 * 解耦、从 V1 重新开始（迁移方案 §9.3）。迁移脚本位于 {@code db/migration/{mysql,postgresql}}
 * 目录，H2 basic Profile 通过 {@code db/basic-schema.sql}（spring.sql.init）初始化表结构。</p>
 *
 * <p>本测试从以下维度保证三份脚本一致且与代码（PO 类）对齐：</p>
 * <ul>
 *   <li>版本链 V1 连续且在 MySQL/PostgreSQL 两个方言目录完全一致；</li>
 *   <li>4 张表在两方言中的列集合、索引、唯一约束完全一致（方言差异仅限类型）；</li>
 *   <li>H2 {@code basic-schema.sql} 的表集合与 Flyway 迁移表集合一致；</li>
 *   <li>以代码实现（PO 类）为唯一事实来源：每个持久化字段必然对应迁移脚本中的列
 *       （MyBatis-Plus map-underscore-to-camel-case）。</li>
 * </ul>
 *
 * @author Apex Team
 */
class WorkflowFlywayMigrationTest {

    private static final Pattern VERSION_PATTERN = Pattern.compile("V([0-9_]+)__.+\\.sql");

    private static final String MYSQL = "mysql";
    private static final String POSTGRESQL = "postgresql";

    private static final String[] MIGRATION_DIALECTS = {MYSQL, POSTGRESQL};

    /** 迁移声明的 4 张工作流表。 */
    private static final Set<String> EXPECTED_TABLES =
            Set.of("wf_workflow_definition", "wf_process_instance", "wf_task", "wf_approval_record");

    /** 所有表必须具备的通用审计列（继承 BasePO，源码事实：id/create_time/update_time/deleted）。 */
    private static final Set<String> AUDIT_COLUMNS = Set.of("id", "create_time", "update_time", "deleted");

    /** 各表除通用审计列外必须具备的业务列。 */
    private static final Map<String, Set<String>> REQUIRED_COLUMNS = Map.ofEntries(
            Map.entry(
                    "wf_workflow_definition",
                    Set.of(
                            "tenant_id",
                            "workflow_code",
                            "workflow_name",
                            "description",
                            "node_definition",
                            "version",
                            "enabled",
                            "remark")),
            Map.entry(
                    "wf_process_instance",
                    Set.of(
                            "tenant_id",
                            "workflow_definition_id",
                            "business_key",
                            "current_node_key",
                            "status",
                            "initiator_id",
                            "form_data",
                            "start_time",
                            "end_time",
                            "remark",
                            "suspended")),
            Map.entry(
                    "wf_task",
                    Set.of(
                            "tenant_id",
                            "process_instance_id",
                            "node_code",
                            "task_name",
                            "assignee_id",
                            "status",
                            "due_time",
                            "complete_time",
                            "remark",
                            "timeout_at",
                            "timeout_strategy",
                            "timeout_handled",
                            "transferred_from")),
            Map.entry(
                    "wf_approval_record",
                    Set.of(
                            "tenant_id",
                            "process_instance_id",
                            "task_id",
                            "node_code",
                            "approver_id",
                            "action",
                            "opinion",
                            "remark")));

    /** 各表必须具备的索引/唯一约束名（MySQL 带反引号，PostgreSQL 不带，故按裸名断言）。 */
    private static final Map<String, Set<String>> REQUIRED_INDEXES = Map.ofEntries(
            Map.entry(
                    "wf_workflow_definition",
                    Set.of(
                            "uk_wf_def_code_version",
                            "idx_wf_def_tenant",
                            "idx_wf_def_enabled",
                            "idx_wf_def_update_time")),
            Map.entry(
                    "wf_process_instance",
                    Set.of(
                            "uk_wf_instance_business_key",
                            "idx_wf_instance_def",
                            "idx_wf_instance_tenant_initiator",
                            "idx_wf_instance_status",
                            "idx_wf_instance_suspended",
                            "idx_wf_instance_start_time",
                            "idx_wf_instance_end_time")),
            Map.entry(
                    "wf_task",
                    Set.of(
                            "idx_wf_task_instance",
                            "idx_wf_task_tenant_assignee_status",
                            "idx_wf_task_status_assignee",
                            "idx_wf_task_due_time",
                            "idx_wf_task_timeout",
                            "idx_wf_task_transferred_from",
                            "idx_wf_task_create_time")),
            Map.entry(
                    "wf_approval_record",
                    Set.of(
                            "idx_wf_approval_instance",
                            "idx_wf_approval_task",
                            "idx_wf_approval_tenant_approver",
                            "idx_wf_approval_create_time")));

    /** 以 PO 类为唯一事实来源：表名 → 持久化对象。 */
    private static final Map<String, Class<?>> PO_TABLES = Map.of(
            "wf_workflow_definition", WorkflowDefinitionPO.class,
            "wf_process_instance", ProcessInstancePO.class,
            "wf_task", TaskPO.class,
            "wf_approval_record", ApprovalRecordPO.class);

    @Test
    void shouldHaveUniqueContiguousVersions() throws Exception {
        List<BigDecimal> mysqlVersions = migrationVersions(MYSQL);
        List<BigDecimal> postgresqlVersions = migrationVersions(POSTGRESQL);

        assertThat(mysqlVersions)
                .as("MySQL and PostgreSQL migration versions must be identical")
                .containsExactlyElementsOf(postgresqlVersions);

        assertThat(mysqlVersions)
                .as("Migration versions must be contiguous starting from 1")
                .containsExactlyElementsOf(IntStream.rangeClosed(1, mysqlVersions.size())
                        .mapToObj(i -> versionNumber(String.valueOf(i)))
                        .toList());
    }

    @Test
    void shouldDeclareIdenticalTablesAndColumnsAcrossDialects() throws Exception {
        String mysqlSql = readMigrationSql(MYSQL);
        String postgresqlSql = readMigrationSql(POSTGRESQL);

        Set<String> mysqlTables = declaredTables(mysqlSql);
        Set<String> postgresqlTables = declaredTables(postgresqlSql);

        assertThat(mysqlTables).as("MySQL 迁移表集合").containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
        assertThat(postgresqlTables).as("PostgreSQL 迁移表集合").containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);

        for (String table : EXPECTED_TABLES) {
            assertThat(tableColumns(mysqlSql, table))
                    .as("表 %s 的 MySQL/PostgreSQL 列集合应完全一致（方言差异仅限类型）", table)
                    .containsExactlyInAnyOrderElementsOf(tableColumns(postgresqlSql, table));
        }
    }

    @Test
    void shouldAlignBasicSchemaTableSetWithFlyway() throws Exception {
        Set<String> basicTables = declaredTables(readBasicSchemaSql());

        assertThat(basicTables)
                .as("H2 basic-schema 表集合应与 Flyway 迁移表集合一致")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
    }

    @Test
    void shouldDefineRequiredColumnsAndIndexes() throws Exception {
        String mysqlSql = readMigrationSql(MYSQL);
        String postgresqlSql = readMigrationSql(POSTGRESQL);
        String basicSql = readBasicSchemaSql();

        for (String table : EXPECTED_TABLES) {
            assertThat(tableColumns(mysqlSql, table))
                    .as("MySQL 表 %s 必须具备要求的列（含通用审计列）", table)
                    .containsAll(withAudit(REQUIRED_COLUMNS.get(table)));
            assertThat(tableColumns(postgresqlSql, table))
                    .as("PostgreSQL 表 %s 必须具备要求的列（含通用审计列）", table)
                    .containsAll(withAudit(REQUIRED_COLUMNS.get(table)));
            assertThat(tableColumns(basicSql, table))
                    .as("H2 basic-schema 表 %s 必须具备要求的列（含通用审计列）", table)
                    .containsAll(withAudit(REQUIRED_COLUMNS.get(table)));

            assertThat(mysqlSql).as("MySQL 表 %s 必须具备要求的索引/唯一约束", table).contains(REQUIRED_INDEXES.get(table));
            assertThat(postgresqlSql)
                    .as("PostgreSQL 表 %s 必须具备要求的索引/唯一约束", table)
                    .contains(REQUIRED_INDEXES.get(table));
        }
    }

    @Test
    void shouldKeepPoFieldsLeadingMigrationColumns() throws Exception {
        String mysqlSql = readMigrationSql(MYSQL);
        String postgresqlSql = readMigrationSql(POSTGRESQL);
        String basicSql = readBasicSchemaSql();

        for (Map.Entry<String, Class<?>> entry : PO_TABLES.entrySet()) {
            String table = entry.getKey();
            Set<String> persistedColumns = persistedColumns(entry.getValue());

            assertThat(tableColumns(mysqlSql, table))
                    .as("MySQL 表 %s 列必须对齐 PO 持久化字段（源码为唯一事实来源）", table)
                    .containsAll(persistedColumns);
            assertThat(tableColumns(postgresqlSql, table))
                    .as("PostgreSQL 表 %s 列必须对齐 PO 持久化字段（源码为唯一事实来源）", table)
                    .containsAll(persistedColumns);
            assertThat(tableColumns(basicSql, table))
                    .as("H2 basic-schema 表 %s 列必须对齐 PO 持久化字段（源码为唯一事实来源）", table)
                    .containsAll(persistedColumns);
        }
    }

    @Test
    void shouldProvideMapperNativeSqlDependentColumns() throws Exception {
        String mysqlSql = readMigrationSql(MYSQL);
        String postgresqlSql = readMigrationSql(POSTGRESQL);
        String basicSql = readBasicSchemaSql();

        // TaskMapper 原生 SQL 依赖 timeout_handled / transferred_from / status / deleted 等列。
        for (String sql : List.of(mysqlSql, postgresqlSql, basicSql)) {
            assertThat(tableColumns(sql, "wf_task"))
                    .as("wf_task 必须具备 Mapper 原生 SQL 依赖的列")
                    .contains("timeout_handled", "transferred_from", "status", "deleted");
        }
    }

    /** 拼接通用审计列与业务列。 */
    private Set<String> withAudit(Set<String> business) {
        Set<String> combined = new LinkedHashSet<>(AUDIT_COLUMNS);
        combined.addAll(business);
        return combined;
    }

    /** 汇总 PO 的持久化列：BasePO 审计列 + 子类声明的字段列（遵循 @TableField 与驼峰转下划线）。 */
    private Set<String> persistedColumns(Class<?> poClass) {
        Set<String> columns = new LinkedHashSet<>(AUDIT_COLUMNS);
        for (Field field : poClass.getDeclaredFields()) {
            TableField tableField = field.getAnnotation(TableField.class);
            if (tableField != null && !tableField.value().isEmpty()) {
                columns.add(tableField.value());
            } else {
                columns.add(toSnakeCase(field.getName()));
            }
        }
        return columns;
    }

    /** 解析迁移文件中的版本号列表。 */
    private List<BigDecimal> migrationVersions(String database) throws Exception {
        try (var files = Files.list(migrationDirectory(database))) {
            return files.map(path -> path.getFileName().toString())
                    .filter(filename -> VERSION_PATTERN.matcher(filename).matches())
                    .map(this::version)
                    .sorted()
                    .toList();
        }
    }

    /** 从迁移文件名提取可排序的 Flyway 版本。 */
    private BigDecimal version(String filename) {
        Matcher matcher = VERSION_PATTERN.matcher(filename == null ? "" : filename);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("非法 Flyway 文件名: " + filename);
        }
        return versionNumber(matcher.group(1).replace('_', '.'));
    }

    private BigDecimal versionNumber(String value) {
        return new BigDecimal(value);
    }

    private String readMigrationSql(String database) throws Exception {
        return Files.readString(migrationSqlPath(database, "V1__init_workflow_schema.sql"));
    }

    private String readBasicSchemaSql() throws Exception {
        return Files.readString(basicSchemaPath());
    }

    /** 解析脚本中声明（CREATE TABLE）的表名集合。 */
    private Set<String> declaredTables(String sql) {
        Pattern tablePattern = Pattern.compile("(?i)CREATE TABLE (?:IF NOT EXISTS )?`?([a-z_]+)`?\\s*\\(");
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = tablePattern.matcher(sql);
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }

    private Path migrationDirectory(String database) {
        Path root = Path.of(System.getProperty("user.dir"));
        Path direct = root.resolve("src/main/resources/db/migration/" + database);
        if (Files.isDirectory(direct)) {
            return direct;
        }
        return root.resolve("apex-workflow-service/src/main/resources/db/migration/" + database);
    }

    private Path migrationSqlPath(String database, String filename) {
        return migrationDirectory(database).resolve(filename);
    }

    private Path basicSchemaPath() {
        Path root = Path.of(System.getProperty("user.dir"));
        Path direct = root.resolve("src/main/resources/db/basic-schema.sql");
        if (Files.exists(direct)) {
            return direct;
        }
        return root.resolve("apex-workflow-service/src/main/resources/db/basic-schema.sql");
    }

    /** 提取指定 CREATE TABLE 语句块内声明的列名集合（兼容带/不带反引号与大小写类型）。 */
    private Set<String> tableColumns(String sql, String table) {
        Pattern blockPattern = Pattern.compile(
                "(?is)CREATE TABLE (?:IF NOT EXISTS )?`?" + table + "`?\\s*\\((.*?)\\)\\s*(?:ENGINE|;)");
        Matcher blockMatcher = blockPattern.matcher(sql);
        if (!blockMatcher.find()) {
            throw new IllegalArgumentException("未找到表定义: " + table);
        }
        String body = blockMatcher.group(1);

        Pattern columnPattern = Pattern.compile("(?m)^\\s*`?([a-z][a-z0-9_]*)`?\\s+[A-Z][A-Z0-9_]*");
        Set<String> columns = new LinkedHashSet<>();
        Matcher columnMatcher = columnPattern.matcher(body);
        while (columnMatcher.find()) {
            columns.add(columnMatcher.group(1));
        }
        return columns;
    }

    /** 将 Java 驼峰字段名转换为数据库下划线列名。 */
    private String toSnakeCase(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
