package com.apex.scaffold.fileservice.db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.file.infrastructure.persistence.po.ChartPO;
import com.apex.scaffold.file.infrastructure.persistence.po.DocumentPO;
import com.apex.scaffold.file.infrastructure.persistence.po.FileChunkPO;
import com.apex.scaffold.file.infrastructure.persistence.po.FilePO;
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
 * <p>文件服务（apex-file-service）使用独立库 {@code apex_file}，迁移版本链与旧单体
 * 解耦、从 V1 重新开始（迁移方案 §9.3）。迁移脚本位于 {@code db/migration/{mysql,postgresql}}
 * 目录，H2 basic Profile 通过 {@code db/basic-schema.sql}（spring.sql.init）初始化表结构。</p>
 *
 * <p>本测试从以下维度保证三份脚本一致且与代码（PO 类）对齐：</p>
 * <ul>
 *   <li>版本链 V1、V2 连续且在 MySQL/PostgreSQL 两个方言目录完全一致；</li>
 *   <li>4 张表在两方言中的列集合（含 ALTER TABLE 增量列）完全一致（方言差异仅限类型）；</li>
 *   <li>H2 {@code basic-schema.sql} 的表集合与 Flyway 迁移表集合一致；</li>
 *   <li>以代码实现（PO 类）为唯一事实来源：每个持久化字段必然对应迁移脚本中的列
 *       （MyBatis-Plus map-underscore-to-camel-case）。</li>
 * </ul>
 *
 * @author Apex Team
 */
class FileFlywayMigrationTest {

    private static final Pattern VERSION_PATTERN = Pattern.compile("V([0-9_]+)__.+\\.sql");

    private static final String MYSQL = "mysql";
    private static final String POSTGRESQL = "postgresql";

    private static final String[] MIGRATION_DIALECTS = {MYSQL, POSTGRESQL};

    /** 迁移声明的 4 张文件服务表。 */
    private static final Set<String> EXPECTED_TABLES =
            Set.of("file_record", "file_document", "file_chart", "file_chunk");

    /** 所有表必须具备的通用审计列（继承 BasePO，源码事实：id/create_time/update_time/deleted）。 */
    private static final Set<String> AUDIT_COLUMNS = Set.of("id", "create_time", "update_time", "deleted");

    /** 各表除通用审计列外必须具备的业务列（version 为乐观锁列，非审计列）。 */
    private static final Map<String, Set<String>> REQUIRED_COLUMNS = Map.ofEntries(
            Map.entry(
                    "file_record",
                    Set.of(
                            "file_name",
                            "original_name",
                            "file_path",
                            "file_url",
                            "file_size",
                            "file_type",
                            "content_type",
                            "bucket_name",
                            "md5",
                            "user_id",
                            "business_type",
                            "client_request_id",
                            "storage_status",
                            "sha256",
                            "version")),
            Map.entry(
                    "file_document",
                    Set.of(
                            "title",
                            "doc_type",
                            "category",
                            "content",
                            "file_url",
                            "file_size",
                            "author_id",
                            "author_name",
                            "status",
                            "tags",
                            "view_count",
                            "download_count",
                            "tenant_id",
                            "version")),
            Map.entry(
                    "file_chart",
                    Set.of(
                            "chart_name",
                            "chart_type",
                            "category",
                            "description",
                            "data_source",
                            "config",
                            "creator_id",
                            "creator_name",
                            "status",
                            "view_count",
                            "tenant_id",
                            "version")),
            Map.entry(
                    "file_chunk",
                    Set.of(
                            "file_id",
                            "chunk_index",
                            "chunk_size",
                            "total_chunks",
                            "md5",
                            "object_name",
                            "bucket_name",
                            "storage_status",
                            "user_id",
                            "version")));

    /** 各表必须具备的索引名（MySQL 带反引号，PostgreSQL 不带，故按裸名断言）。 */
    private static final Map<String, Set<String>> REQUIRED_INDEXES = Map.ofEntries(
            Map.entry(
                    "file_record",
                    Set.of(
                            "idx_file_record_user",
                            "idx_file_record_business_type",
                            "idx_file_record_client_request_id",
                            "idx_file_record_create_time")),
            Map.entry(
                    "file_document",
                    Set.of(
                            "idx_file_document_tenant",
                            "idx_file_document_title",
                            "idx_file_document_author",
                            "idx_file_document_status",
                            "idx_file_document_create_time")),
            Map.entry(
                    "file_chart",
                    Set.of(
                            "idx_file_chart_tenant",
                            "idx_file_chart_name",
                            "idx_file_chart_creator",
                            "idx_file_chart_status",
                            "idx_file_chart_create_time")),
            Map.entry(
                    "file_chunk",
                    Set.of(
                            "idx_file_chunk_file",
                            "idx_file_chunk_file_index",
                            "idx_file_chunk_status",
                            "idx_file_chunk_create_time")));

    /** 以 PO 类为唯一事实来源：表名 → 持久化对象。 */
    private static final Map<String, Class<?>> PO_TABLES = Map.of(
            "file_record", FilePO.class,
            "file_document", DocumentPO.class,
            "file_chart", ChartPO.class,
            "file_chunk", FileChunkPO.class);

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
    void shouldDefineRequiredColumnsAndIndexes() throws Exception {
        String mysqlSql = readMigrationSql(MYSQL);
        String postgresqlSql = readMigrationSql(POSTGRESQL);
        String basicSql = readBasicSchemaSql();

        for (String table : EXPECTED_TABLES) {
            assertThat(allColumns(mysqlSql, table))
                    .as("MySQL 表 %s 必须具备要求的列（含通用审计列）", table)
                    .containsAll(withAudit(REQUIRED_COLUMNS.get(table)));
            assertThat(allColumns(postgresqlSql, table))
                    .as("PostgreSQL 表 %s 必须具备要求的列（含通用审计列）", table)
                    .containsAll(withAudit(REQUIRED_COLUMNS.get(table)));
            assertThat(allColumns(basicSql, table))
                    .as("H2 basic-schema 表 %s 必须具备要求的列（含通用审计列）", table)
                    .containsAll(withAudit(REQUIRED_COLUMNS.get(table)));

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

    @Test
    void shouldProvideMapperNativeSqlDependentColumns() throws Exception {
        String mysqlSql = readMigrationSql(MYSQL);
        String postgresqlSql = readMigrationSql(POSTGRESQL);
        String basicSql = readBasicSchemaSql();

        // FileMapper.markStorageDeleted/markStorageApproved 原生 SQL 依赖 storage_status/update_time/deleted。
        // FileChunkMapper.markDeletedByFileId 原生 SQL 依赖 file_id/update_time/deleted。
        for (String sql : List.of(mysqlSql, postgresqlSql, basicSql)) {
            assertThat(allColumns(sql, "file_record"))
                    .as("file_record 必须具备 Mapper 原生 SQL 依赖的列")
                    .contains("storage_status", "update_time", "deleted");
            assertThat(allColumns(sql, "file_chunk"))
                    .as("file_chunk 必须具备 Mapper 原生 SQL 依赖的列")
                    .contains("file_id", "update_time", "deleted");
        }
    }

    /** 拼接通用审计列与业务列。 */
    private Set<String> withAudit(Set<String> business) {
        Set<String> combined = new LinkedHashSet<>(AUDIT_COLUMNS);
        combined.addAll(business);
        return combined;
    }

    /**
     * 汇总 PO 的持久化列：BasePO 审计列 + 子类声明的字段列（遵循 @TableField 与驼峰转下划线）。
     *
     * <p>静态字段（如 {@code serialVersionUID}）不对应数据库列，必须跳过。</p>
     */
    private Set<String> persistedColumns(Class<?> poClass) {
        Set<String> columns = new LinkedHashSet<>(AUDIT_COLUMNS);
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

    /** 读取指定方言目录下全部迁移脚本（含 V1、V2 等增量脚本）并拼接。 */
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
        return root.resolve("apex-file-service/src/main/resources/db/migration/" + database);
    }

    private Path basicSchemaPath() {
        Path root = Path.of(System.getProperty("user.dir"));
        Path direct = root.resolve("src/main/resources/db/basic-schema.sql");
        if (Files.exists(direct)) {
            return direct;
        }
        return root.resolve("apex-file-service/src/main/resources/db/basic-schema.sql");
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
            Pattern addPattern = Pattern.compile("(?i)ADD COLUMN\\s+`?([a-z][a-z0-9_]*)`?");
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
