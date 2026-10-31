package com.apex.scaffold.officeservice.db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.office.infrastructure.persistence.OfficeAppPO;
import com.apex.scaffold.office.infrastructure.persistence.OfficeBindingPO;
import com.apex.scaffold.office.infrastructure.persistence.OfficeOrganizationMappingPO;
import com.apex.scaffold.office.infrastructure.persistence.OfficeSyncTaskPO;
import com.apex.scaffold.office.infrastructure.persistence.po.OfficeMessagePO;
import com.apex.scaffold.office.infrastructure.po.ApprovalProcessPO;
import com.apex.scaffold.office.infrastructure.po.CalendarEventPO;
import com.apex.scaffold.office.infrastructure.po.DocumentInfoPO;
import com.apex.scaffold.office.meetingroom.infrastructure.persistence.po.MeetingRoomPO;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffDepartmentPO;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffEmployeePO;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffMessageDeliveryPO;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffMessagePO;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffMessageRecipientPO;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffMessageTemplatePO;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffSyncHistoryPO;
import com.baomidou.mybatisplus.annotation.TableField;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
 * <p>办公服务（apex-office-service）使用独立库 {@code apex_office}，迁移版本链与旧单体
 * 解耦、从 V1 重新开始（迁移方案 §9.3）。迁移脚本位于 {@code db/migration/{mysql,postgresql}}
 * 目录，H2 basic Profile 通过 {@code db/basic-schema.sql}（spring.sql.init）初始化表结构。</p>
 *
 * <p>本测试从以下维度保证三份脚本一致且与代码（PO 类）对齐：</p>
 * <ul>
 *   <li>版本链连续且在 MySQL/PostgreSQL 两个方言目录完全一致；</li>
 *   <li>16 张表（9 张 office_* + 7 张 off_*，含 V5 off_message_delivery 与 V7 office_meeting_room）在两方言中的列集合完全一致（方言差异仅限类型）；</li>
 *   <li>H2 {@code basic-schema.sql} 的表集合与 Flyway 迁移表集合一致；</li>
 *   <li>以代码实现（PO 类）为唯一事实来源：每个持久化字段必然对应迁移脚本中的列
 *       （MyBatis-Plus map-underscore-to-camel-case）。</li>
 * </ul>
 *
 * <p>注意：{@code office_approval_process} / {@code office_message} 使用
 * {@code created_at}/{@code updated_at}（与 PO 定义一致），其余表使用
 * {@code create_time}/{@code update_time}，故本测试不做全局审计列假设。</p>
 *
 * @author Apex Team
 */
class OfficeFlywayMigrationTest {

    private static final Pattern VERSION_PATTERN = Pattern.compile("V([0-9_]+)__.+\\.sql");

    private static final String MYSQL = "mysql";
    private static final String POSTGRESQL = "postgresql";

    /**
     * 迁移声明的 16 张办公服务表（V1 八张 office_* + V2 六张 off_* + V5 一张 off_message_delivery
     * + V7 一张 office_meeting_room）。
     */
    private static final Set<String> EXPECTED_TABLES = Set.of(
            "office_platform_app",
            "office_identity_binding",
            "office_organization_mapping",
            "office_sync_task",
            "office_message",
            "office_approval_process",
            "office_calendar_event",
            "office_document_info",
            "off_message_template",
            "off_message",
            "off_message_recipient",
            "off_message_delivery",
            "off_department",
            "off_employee",
            "off_sync_history",
            "office_meeting_room");

    /** 各表必须具备的索引名（MySQL 带反引号，PostgreSQL 不带，故按裸名断言）。 */
    private static final Map<String, Set<String>> REQUIRED_INDEXES = Map.ofEntries(
            Map.entry("office_platform_app", Set.of("uk_office_app_tenant_platform")),
            Map.entry("office_identity_binding", Set.of("uk_office_binding_external", "uk_office_binding_user")),
            Map.entry("office_organization_mapping", Set.of("uk_office_org")),
            Map.entry("office_sync_task", Set.of("uk_office_sync")),
            Map.entry(
                    "office_message",
                    Set.of(
                            "idx_office_message_tenant_platform",
                            "idx_office_message_tenant_status",
                            "idx_office_message_platform_message_id",
                            "idx_office_message_sent_at")),
            Map.entry(
                    "office_approval_process",
                    Set.of(
                            "idx_office_approval_tenant",
                            "idx_office_approval_platform_instance",
                            "idx_office_approval_process_instance",
                            "idx_office_approval_initiator",
                            "idx_office_approval_status",
                            "idx_office_approval_created_at")),
            Map.entry(
                    "office_calendar_event",
                    Set.of(
                            "idx_office_calendar_tenant",
                            "idx_office_calendar_platform_event",
                            "idx_office_calendar_organizer",
                            "idx_office_calendar_time_range",
                            "idx_office_calendar_status",
                            "idx_office_calendar_create_time")),
            Map.entry(
                    "office_document_info",
                    Set.of(
                            "idx_office_document_tenant",
                            "idx_office_document_platform_doc",
                            "idx_office_document_owner",
                            "idx_office_document_parent",
                            "idx_office_document_title",
                            "idx_office_document_archived",
                            "idx_office_document_create_time")),
            Map.entry(
                    "off_message_template",
                    Set.of("uk_off_message_template_code_tenant", "idx_off_message_template_tenant_enabled")),
            Map.entry(
                    "off_message",
                    Set.of(
                            "uk_off_message_code_tenant",
                            "idx_off_message_tenant_status",
                            "idx_off_message_sender_time",
                            "idx_off_message_template")),
            Map.entry(
                    "off_message_recipient",
                    Set.of("uk_off_message_recipient", "idx_off_recipient_status", "idx_off_recipient_tenant")),
            Map.entry(
                    "off_message_delivery",
                    Set.of("uk_off_message_delivery", "idx_off_delivery_recipient_status", "idx_off_delivery_tenant")),
            Map.entry(
                    "off_department",
                    Set.of(
                            "uk_off_department_code_tenant",
                            "idx_off_department_parent_tenant",
                            "idx_off_department_sync")),
            Map.entry(
                    "off_employee",
                    Set.of(
                            "uk_off_employee_code_tenant",
                            "idx_off_employee_dept",
                            "idx_off_employee_user",
                            "idx_off_employee_sync")),
            Map.entry("off_sync_history", Set.of("idx_off_sync_tenant_time", "idx_off_sync_status_time")),
            Map.entry(
                    "office_meeting_room",
                    Set.of(
                            "uk_office_meeting_room_tenant_code",
                            "uk_office_meeting_room_tenant_name",
                            "idx_office_meeting_room_tenant_status")));

    /** 以 PO 类为唯一事实来源：表名 → 持久化对象。 */
    private static final Map<String, Class<?>> PO_TABLES = Map.ofEntries(
            Map.entry("office_platform_app", OfficeAppPO.class),
            Map.entry("office_identity_binding", OfficeBindingPO.class),
            Map.entry("office_organization_mapping", OfficeOrganizationMappingPO.class),
            Map.entry("office_sync_task", OfficeSyncTaskPO.class),
            Map.entry("office_message", OfficeMessagePO.class),
            Map.entry("office_approval_process", ApprovalProcessPO.class),
            Map.entry("office_calendar_event", CalendarEventPO.class),
            Map.entry("office_document_info", DocumentInfoPO.class),
            Map.entry("off_message_template", OffMessageTemplatePO.class),
            Map.entry("off_message", OffMessagePO.class),
            Map.entry("off_message_recipient", OffMessageRecipientPO.class),
            Map.entry("off_message_delivery", OffMessageDeliveryPO.class),
            Map.entry("off_department", OffDepartmentPO.class),
            Map.entry("off_employee", OffEmployeePO.class),
            Map.entry("off_sync_history", OffSyncHistoryPO.class),
            Map.entry("office_meeting_room", MeetingRoomPO.class));

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

        Set<String> migrationTables = new LinkedHashSet<>(EXPECTED_TABLES);
        migrationTables.add("undo_log");
        assertThat(mysqlTables).as("MySQL 迁移表集合").containsExactlyInAnyOrderElementsOf(migrationTables);
        assertThat(postgresqlTables).as("PostgreSQL 迁移表集合").containsExactlyInAnyOrderElementsOf(migrationTables);
        Set<String> undoColumns =
                Set.of("branch_id", "xid", "context", "rollback_info", "log_status", "log_created", "log_modified");
        assertThat(allColumns(mysqlSql, "undo_log")).containsExactlyInAnyOrderElementsOf(undoColumns);
        assertThat(allColumns(postgresqlSql, "undo_log")).containsExactlyInAnyOrderElementsOf(undoColumns);

        for (String table : migrationTables) {
            assertThat(allColumns(mysqlSql, table))
                    .as("表 %s 的 MySQL/PostgreSQL 列集合应完全一致（含 ALTER TABLE 增量列）", table)
                    .containsExactlyInAnyOrderElementsOf(allColumns(postgresqlSql, table));
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
    void shouldDefineRequiredIndexes() throws Exception {
        String mysqlSql = readMigrationSql(MYSQL);
        String postgresqlSql = readMigrationSql(POSTGRESQL);

        for (String table : EXPECTED_TABLES) {
            assertThat(mysqlSql).as("MySQL 表 %s 必须具备要求的索引", table).contains(REQUIRED_INDEXES.get(table));
            assertThat(postgresqlSql).as("PostgreSQL 表 %s 必须具备要求的索引", table).contains(REQUIRED_INDEXES.get(table));
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

            assertThat(allColumns(mysqlSql, table))
                    .as("MySQL 表 %s 列必须对齐 PO 持久化字段（源码为唯一事实来源）", table)
                    .containsAll(persistedColumns);
            assertThat(allColumns(postgresqlSql, table))
                    .as("PostgreSQL 表 %s 列必须对齐 PO 持久化字段（源码为唯一事实来源）", table)
                    .containsAll(persistedColumns);
            assertThat(allColumns(basicSql, table))
                    .as("H2 basic-schema 表 %s 列必须对齐 PO 持久化字段（源码为唯一事实来源）", table)
                    .containsAll(persistedColumns);
        }
    }

    /**
     * 汇总 PO 的持久化列：PO 类声明的全部非静态字段列（遵循 @TableField 与驼峰转下划线）。
     *
     * <p>静态字段（如 {@code serialVersionUID}）不对应数据库列，必须跳过。</p>
     */
    private Set<String> persistedColumns(Class<?> poClass) {
        Set<String> columns = new LinkedHashSet<>();
        for (Field field : poClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
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

    /** 读取指定方言目录下全部迁移脚本并拼接。 */
    private String readMigrationSql(String database) throws Exception {
        try (var files = Files.list(migrationDirectory(database))) {
            List<Path> sqlFiles = files.filter(
                            path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
            StringBuilder sql = new StringBuilder();
            for (Path path : sqlFiles) {
                sql.append(Files.readString(path)).append(System.lineSeparator());
            }
            return sql.toString();
        }
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
        return root.resolve("apex-office-service/src/main/resources/db/migration/" + database);
    }

    private Path basicSchemaPath() {
        Path root = Path.of(System.getProperty("user.dir"));
        Path direct = root.resolve("src/main/resources/db/basic-schema.sql");
        if (Files.exists(direct)) {
            return direct;
        }
        return root.resolve("apex-office-service/src/main/resources/db/basic-schema.sql");
    }

    /** 汇总某表的全部列：CREATE TABLE 声明列 + ALTER TABLE ADD COLUMN 增量列。 */
    private Set<String> allColumns(String sql, String table) {
        Set<String> columns = new LinkedHashSet<>(tableColumns(sql, table));
        columns.addAll(addedColumns(sql, table));
        return columns;
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

    /** 提取指定表 ALTER TABLE ... ADD COLUMN 声明的增量列名集合。 */
    private Set<String> addedColumns(String sql, String table) {
        Set<String> columns = new LinkedHashSet<>();
        Pattern alterPattern = Pattern.compile("(?is)ALTER TABLE\\s+`?" + table + "`?\\s+(.*?);");
        Matcher alterMatcher = alterPattern.matcher(sql);
        while (alterMatcher.find()) {
            String body = alterMatcher.group(1);
            // PostgreSQL 允许 ADD COLUMN IF NOT EXISTS，需跳过可选修饰，否则会把 IF 误识别为列名。
            Pattern addPattern = Pattern.compile("(?i)ADD COLUMN\\s+(?:IF NOT EXISTS\\s+)?`?([a-z][a-z0-9_]*)`?");
            Matcher addMatcher = addPattern.matcher(body);
            while (addMatcher.find()) {
                columns.add(addMatcher.group(1));
            }
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
