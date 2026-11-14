package com.apex.scaffold.tools.deployment;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 显式离线 MySQL V42 结构恢复工具，不执行任何迁移历史操作。
 *
 * <p>保持原 {@code token_v42_recover.py} 的契约：源哈希、字段/索引/视图比对、CLI 语义与输出格式。
 * 工具只补齐 V42 结构，原始 V42 重放与 Flyway 历史仍需人工处理。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public final class TokenV42Recover {

    /** 原始 V42 迁移文件的 SHA-256；源文件变化时必须重新复核本工具契约。 */
    public static final String SOURCE_HASH = "df9a7c3a20b21a3be1c80ab8d2c11be37c155fee9ca5001eb1c9ceb02b75299e";

    /** 使用说明。 */
    public static final String USAGE =
            "usage: recover --database DATABASE --offline [--apply] [--mysql-command COMMAND...]";

    /** 退出码：执行成功。 */
    private static final int EXIT_OK = 0;

    /** 退出码：前置检查拒绝或执行失败。 */
    private static final int EXIT_FAILURE = 1;

    /** 退出码：参数无效。 */
    private static final int EXIT_USAGE = 2;

    /** 被校验的迁移脚本相对仓库根的路径。 */
    private static final String V42_SOURCE =
            "apex-system/src/main/resources/db/migration/mysql/V42__add_token_encryption.sql";

    /** 代码源上溯层数：{@code target/classes} 与 {@code target/local-deployment.jar} 均为 3 级。 */
    private static final int SOURCE_PARENT_LEVELS = 3;

    /** 期望的新增列数量，防止源文件被静默改写。 */
    private static final int EXPECTED_COLUMN_COUNT = 10;

    /** JSON 转义时低于该码点的字符使用 \\uXXXX 形式。 */
    private static final int CONTROL_LIMIT = 0x20;

    private static final String OAUTH2_AUTHORIZATION = "oauth2_authorization";

    private static final String TOKEN = "token";

    private static final String VIEW_NAME = "v_token_health";

    private static final String KEY_VERSION_TABLE = "encryption_key_version";

    private static final String ISSUED_AT = "access_token_issued_at";

    private static final String EXPIRES_AT = "access_token_expires_at";

    private static final String BASE_TABLE = "BASE TABLE";

    private static final String VIEW_TABLE = "VIEW";

    private static final String INNODB = "InnoDB";

    private static final String UTF8MB4 = "utf8mb4";

    private static final String UTF8MB4_PREFIX = "utf8mb4_";

    private static final String DATETIME = "datetime";

    private static final String TIMESTAMP = "timestamp";

    private static final String VARCHAR = "varchar";

    private static final String TEXT = "text";

    private static final String BOOLEAN = "boolean";

    private static final String TINYINT_ONE = "tinyint(1)";

    private static final String MYSQL_VERSION_PREFIX = "8.0.";

    private static final String CHECK_OPTION_NONE = "NONE";

    private static final String SECURITY_TYPE_DEFINER = "DEFINER";

    private static final String DEFAULT_MYSQL_COMMAND = "mysql";

    private static final String NO_DEFAULTS = "--no-defaults";

    private static final String DATABASE_OPTION = "--database";

    private static final String DATABASE_PREFIX = "--database=";

    private static final String OFFLINE_OPTION = "--offline";

    private static final String APPLY_OPTION = "--apply";

    private static final String MYSQL_COMMAND_OPTION = "--mysql-command";

    private static final String MYSQL_COMMAND_PREFIX = "--mysql-command=";

    private static final String FORCE_OPTION = "--force";

    private static final String HELP_OPTION = "--help";

    private static final String HELP_SHORT_OPTION = "-h";

    private static final String VERSION_PROBE = "SELECT VERSION();";

    private static final String VIEW_PROBE = "SELECT * FROM v_token_health LIMIT 0;";

    private static final String KEY_VERSION_PROBE =
            "SELECT JSON_ARRAY(key_version) FROM encryption_key_version WHERE key_version=1;";

    private static final String KEY_VERSION_INSERT =
            "INSERT INTO encryption_key_version(key_version,key_id,active) SELECT 1,'default-key-v1',TRUE"
                    + " WHERE NOT EXISTS (SELECT 1 FROM encryption_key_version WHERE key_version=1);";

    private static final String APPLY_COMPLETE =
            "structure recovery complete; original V42 replay and Flyway history remain unresolved";

    private static final String TABLES_QUERY = "SELECT JSON_ARRAY(table_name,table_type,engine,table_collation)"
            + " FROM information_schema.tables WHERE table_schema=DATABASE()";

    private static final String COLUMNS_QUERY =
            "SELECT JSON_ARRAY(table_name,column_name,column_type,is_nullable,column_default,extra,"
                    + "generation_expression,character_set_name,collation_name)"
                    + " FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY ordinal_position";

    private static final String INDEXES_QUERY =
            "SELECT JSON_ARRAY(table_name,index_name,non_unique,column_name,sub_part,collation,index_type,"
                    + "is_visible,expression) FROM information_schema.statistics"
                    + " WHERE table_schema=DATABASE() ORDER BY index_name,seq_in_index";

    private static final String TRIGGERS_QUERY = "SELECT JSON_ARRAY(trigger_name) FROM information_schema.triggers"
            + " WHERE trigger_schema=DATABASE() AND event_object_table='";

    private static final String CONSTRAINTS_QUERY =
            "SELECT JSON_ARRAY(constraint_name) FROM information_schema.table_constraints"
                    + " WHERE table_schema=DATABASE() AND table_name='";

    private static final String CONSTRAINTS_FILTER = "' AND constraint_type IN ('CHECK','FOREIGN KEY')";

    private static final String VIEW_QUERY = "SELECT JSON_ARRAY(view_definition,check_option,security_type)"
            + " FROM information_schema.views WHERE table_schema=DATABASE() AND table_name='";

    /** information_schema.tables 行下标。 */
    private static final int TABLE_NAME = 0;

    private static final int TABLE_TYPE = 1;

    private static final int TABLE_ENGINE = 2;

    private static final int TABLE_COLLATION = 3;

    /** information_schema.columns 行下标，与 {@link #COLUMNS_QUERY} 的 JSON_ARRAY 顺序一致。 */
    private static final int COLUMN_TABLE = 0;

    private static final int COLUMN_NAME = 1;

    private static final int COLUMN_TYPE = 2;

    private static final int COLUMN_NULLABLE = 3;

    private static final int COLUMN_DEFAULT = 4;

    private static final int COLUMN_EXTRA = 5;

    private static final int COLUMN_GENERATION = 6;

    private static final int COLUMN_CHARSET = 7;

    /** information_schema.statistics 行下标；{@code non_unique} 之后整段保留为索引行。 */
    private static final int INDEX_TABLE = 0;

    private static final int INDEX_NAME = 1;

    private static final int INDEX_NON_UNIQUE = 2;

    /** information_schema.views 行下标。 */
    private static final int VIEW_DEFINITION = 0;

    private static final int VIEW_CHECK_OPTION = 1;

    private static final int VIEW_SECURITY_TYPE = 2;

    private static final Pattern DATABASE_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    /** mysql 客户端短选项中携带 force 的组合，如 {@code -vf}。 */
    private static final Pattern SHORT_FORCE_PATTERN = Pattern.compile("^-[vNst]*f");

    private static final Pattern QUOTED_TOKEN_PATTERN = Pattern.compile("(`(?:``|[^`])*`|'(?:''|[^'])*')");

    private static final Pattern SIMPLE_IDENTIFIER_PATTERN = Pattern.compile("`[A-Za-z0-9_]+`");

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final Pattern COLUMN_CLAUSE_PATTERN = Pattern.compile("(?m)^ADD COLUMN (.*?)(?:,|;)$");

    private static final Pattern TABLE_PATTERN =
            Pattern.compile("(?s)CREATE TABLE IF NOT EXISTS (\\w+) \\((.*?)\\) ENGINE=InnoDB[^;]*;");

    private static final Pattern VIEW_PATTERN = Pattern.compile("(?s)CREATE OR REPLACE VIEW v_token_health AS(.*?);");

    private static final Pattern INDEX_CLAUSE_PATTERN = Pattern.compile("INDEX (\\w+) \\((\\w+)\\)");

    private static final Pattern FIELD_PATTERN =
            Pattern.compile("(?i)(\\w+) (BIGINT|INT|VARCHAR\\(\\d+\\)|TEXT|JSON|DATETIME|BOOLEAN)(.*)");

    private static final Pattern DEFAULT_PATTERN = Pattern.compile("DEFAULT ('[^']*'|\\w+)");

    /** 命令行解析状态。 */
    private enum Mode {
        /** 参数合法，可执行迁移。 */
        VALID,
        /** 命中帮助选项，仅打印用法后退出。 */
        HELP,
        /** 参数非法，需打印用法并以非零码退出。 */
        INVALID
    }

    /**
     * 命令行解析结果。
     *
     * @param mode 解析状态
     * @param database 目标数据库名
     * @param offline 是否为离线模式（仅生成产物，不连库）
     * @param apply 是否实际执行迁移
     * @param command 其余命令行参数
     */
    private record Options(Mode mode, String database, boolean offline, boolean apply, List<String> command) {}

    /**
     * information_schema.tables 中的单表元数据。
     *
     * @param type 表类型（information_schema.tables.table_type）
     * @param engine 存储引擎
     * @param collation 排序规则
     */
    private record TableMeta(String type, String engine, String collation) {}

    private TokenV42Recover() {}

    /**
     * 进程入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        PrintWriter out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        PrintWriter err = new PrintWriter(System.err, true, StandardCharsets.UTF_8);
        System.exit(run(args, out, err, MysqlClient::new, TokenV42Recover::preflight));
    }

    /**
     * 可测试入口：解析命令行、执行 plan/apply 流程并返回退出码。
     *
     * @param args      命令行参数
     * @param out       标准输出
     * @param err       标准错误
     * @param factory   客户端工厂
     * @param preflight 前置检查实现
     * @return 退出码，0 成功、1 失败、2 参数无效
     */
    public static int run(
            String[] args, PrintWriter out, PrintWriter err, RecoverClientFactory factory, PreflightCheck preflight) {
        Options options = parse(args);
        if (options.mode() == Mode.HELP) {
            out.println(USAGE);
            return EXIT_OK;
        }
        List<String> command =
                options.command() == null ? List.of(DEFAULT_MYSQL_COMMAND, NO_DEFAULTS) : options.command();
        if (!valid(options.database(), options.offline(), command)) {
            return usage(err);
        }
        for (String option : command) {
            if (forbidden(option)) {
                return usage(err);
            }
        }
        try {
            String source = Files.readString(sourcePath(), StandardCharsets.UTF_8);
            RecoverClient client = factory.create(command, options.database());
            if (!client.execute(VERSION_PROBE).startsWith(MYSQL_VERSION_PREFIX)) {
                throw new RejectedException("only MySQL 8.0 is supported");
            }
            List<String> plan = preflight.check(client, source);
            out.println(jsonPlan(options.apply(), plan));
            if (options.apply()) {
                applyPlan(client, plan, source, preflight);
                out.println(APPLY_COMPLETE);
            }
            return EXIT_OK;
        } catch (RejectedException error) {
            err.println("preflight rejected: " + error.getMessage());
            return EXIT_FAILURE;
        } catch (Exception error) {
            err.println("recovery stopped: " + error.getMessage());
            return EXIT_FAILURE;
        }
    }

    /**
     * 校验源哈希并解析出新增列、三张契约表与健康视图定义。
     *
     * @param source V42 迁移源文件内容
     * @return 结构契约
     * @throws RejectedException 源哈希或结构不匹配时抛出
     */
    public static SourceContract sourceContract(String source) throws RejectedException {
        if (!sha256(source).equals(SOURCE_HASH)) {
            throw new RejectedException("original V42 hash differs; review tool contract");
        }
        List<String> columns = new ArrayList<>();
        Matcher columnMatcher = COLUMN_CLAUSE_PATTERN.matcher(source);
        while (columnMatcher.find()) {
            columns.add(columnMatcher.group(1));
        }
        if (columns.size() != EXPECTED_COLUMN_COUNT) {
            throw new RejectedException("V42 column contract differs");
        }
        LinkedHashMap<String, SourceTable> tables = new LinkedHashMap<>();
        Matcher tableMatcher = TABLE_PATTERN.matcher(source);
        while (tableMatcher.find()) {
            tables.put(tableMatcher.group(1), parseTable(tableMatcher.group(1), tableMatcher.group(2)));
        }
        Matcher viewMatcher = VIEW_PATTERN.matcher(source);
        if (!viewMatcher.find()) {
            throw new RejectedException("v_token_health definition missing");
        }
        String view = "CREATE VIEW v_token_health AS" + viewMatcher.group(1) + ";";
        return new SourceContract(columns, tables, view);
    }

    /**
     * 规范化视图定义：移除 schema 限定符，压缩引号外空白并统一小写。
     *
     * @param text     视图定义
     * @param database 当前库名
     * @return 规范化结果
     */
    public static String normalizeView(String text, String database) {
        String replaced = text.replace("`" + database + "`.", "");
        Matcher quoted = QUOTED_TOKEN_PATTERN.matcher(replaced);
        StringBuilder normalized = new StringBuilder();
        int position = 0;
        while (quoted.find()) {
            normalized.append(normalizePart(replaced.substring(position, quoted.start()), false));
            normalized.append(normalizePart(quoted.group(1), true));
            position = quoted.end();
        }
        normalized.append(normalizePart(replaced.substring(position), false));
        return normalized.toString();
    }

    /**
     * 全量前置检查，返回需要执行的恢复语句；任何不兼容立即拒绝。
     *
     * @param client 已连接客户端
     * @param source V42 迁移源文件内容
     * @return 恢复语句计划
     * @throws Exception 检查不通过或客户端失败时抛出
     */
    public static List<String> preflight(RecoverClient client, String source) throws Exception {
        SourceContract contract = sourceContract(source);
        Map<String, TableMeta> tables = tableMetadata(client);
        List<ColumnMeta> columns = columnMetadata(client);
        List<List<Object>> indexRows = client.rows(INDEXES_QUERY);
        if (!tables.containsKey(OAUTH2_AUTHORIZATION)) {
            throw new RejectedException("oauth2_authorization missing");
        }
        List<String> plan = new ArrayList<>();
        for (String table : List.of(OAUTH2_AUTHORIZATION, TOKEN)) {
            if (tables.containsKey(table)) {
                appendTokenPlan(client, tables.get(table), columns, indexRows, contract, table, plan);
            }
        }
        for (Map.Entry<String, SourceTable> entry : contract.getTables().entrySet()) {
            appendContractTablePlan(
                    client, tables.get(entry.getKey()), columns, indexRows, entry.getKey(), entry.getValue(), plan);
        }
        appendViewPlan(client, tables, contract, plan);
        appendKeyVersionPlan(client, tables, plan);
        return plan;
    }

    /**
     * 执行计划并做后置校验，确认视图可查询。
     *
     * @param client    已连接客户端
     * @param plan      恢复语句
     * @param source    V42 迁移源文件内容
     * @param preflight 前置检查实现，用于后置复核
     * @throws Exception 语句执行失败或后置校验未通过时抛出
     */
    private static void applyPlan(RecoverClient client, List<String> plan, String source, PreflightCheck preflight)
            throws Exception {
        for (String statement : plan) {
            client.execute(statement);
        }
        if (!preflight.check(client, source).isEmpty()) {
            throw new RejectedException("postflight incomplete: recovery statements remain");
        }
        client.execute(VIEW_PROBE);
    }

    /**
     * 补齐 token 相关表缺少的列与索引。
     *
     * @param client    已连接客户端
     * @param meta      表元数据
     * @param columns   全部列元数据
     * @param indexRows 全部索引行
     * @param contract  源结构契约
     * @param table     表名
     * @param plan      恢复语句收集器
     * @throws Exception 表不兼容或客户端失败时抛出
     */
    private static void appendTokenPlan(
            RecoverClient client,
            TableMeta meta,
            List<ColumnMeta> columns,
            List<List<Object>> indexRows,
            SourceContract contract,
            String table,
            List<String> plan)
            throws Exception {
        checkBase(client, meta, table);
        requireUtf8mb4(meta, table);
        SourceCatalog catalog = catalog(columns, indexRows, table);
        if (OAUTH2_AUTHORIZATION.equals(table)) {
            requireViewDependency(catalog, table, ISSUED_AT);
            requireViewDependency(catalog, table, EXPIRES_AT);
        }
        int position = 0;
        for (Map.Entry<String, List<String>> entry : V42Contract.fields().entrySet()) {
            String definition = contract.getColumns().get(position);
            position++;
            if (catalog.columnNames().contains(entry.getKey())) {
                checkField(catalog, table, entry.getKey(), entry.getValue());
            } else {
                plan.add("ALTER TABLE `" + table + "` ADD COLUMN " + definition + ";");
            }
        }
        for (Map.Entry<String, List<List<Object>>> entry : V42Contract.indexes().entrySet()) {
            List<List<Object>> expected = entry.getValue();
            List<List<Object>> actual = catalog.index(entry.getKey());
            if (actual == null) {
                plan.add("CREATE INDEX `" + entry.getKey() + "` ON `" + table + "` (`"
                        + expected.get(0).get(1) + "`);");
            } else if (!actual.equals(expected)) {
                throw new RejectedException(table + "." + entry.getKey() + ": incompatible index");
            }
        }
    }

    /**
     * 校验或重建契约表。
     *
     * @param client    已连接客户端
     * @param meta      表元数据，表不存在时为 null
     * @param columns   全部列元数据
     * @param indexRows 全部索引行
     * @param table     表名
     * @param expected  期望表结构
     * @param plan      恢复语句收集器
     * @throws Exception 表不兼容或客户端失败时抛出
     */
    private static void appendContractTablePlan(
            RecoverClient client,
            TableMeta meta,
            List<ColumnMeta> columns,
            List<List<Object>> indexRows,
            String table,
            SourceTable expected,
            List<String> plan)
            throws Exception {
        if (meta == null) {
            plan.add(expected.getDdl());
            return;
        }
        checkBase(client, meta, table);
        requireUtf8mb4(meta, table);
        SourceCatalog catalog = catalog(columns, indexRows, table);
        boolean sameFields = catalog.columnNames().equals(expected.getFields().keySet());
        if (!sameFields || !catalog.indexes().equals(expected.getIndexes())) {
            throw new RejectedException(table + ": incompatible column/index set");
        }
        for (Map.Entry<String, List<String>> field : expected.getFields().entrySet()) {
            checkField(catalog, table, field.getKey(), field.getValue());
        }
    }

    /**
     * 校验已有视图定义，或追加创建视图语句。
     *
     * @param client   已连接客户端
     * @param tables   表元数据映射
     * @param contract 源结构契约
     * @param plan     恢复语句收集器
     * @throws Exception 视图不兼容或客户端失败时抛出
     */
    private static void appendViewPlan(
            RecoverClient client, Map<String, TableMeta> tables, SourceContract contract, List<String> plan)
            throws Exception {
        TableMeta meta = tables.get(VIEW_NAME);
        if (meta == null) {
            plan.add(contract.getView());
            return;
        }
        if (!VIEW_TABLE.equals(meta.type())) {
            throw new RejectedException(VIEW_NAME + ": expected view");
        }
        List<Object> row = client.rows(VIEW_QUERY + VIEW_NAME + "'").get(0);
        String actual = normalizeView(text(row.get(VIEW_DEFINITION)), client.database());
        boolean sameDefinition = actual.equals(normalizeView(V42Contract.view(), client.database()));
        if (!sameDefinition
                || !CHECK_OPTION_NONE.equals(text(row.get(VIEW_CHECK_OPTION)))
                || !SECURITY_TYPE_DEFINER.equals(text(row.get(VIEW_SECURITY_TYPE)))) {
            throw new RejectedException(VIEW_NAME + ": incompatible definition/security/check option");
        }
    }

    /**
     * 仅在版本 1 缺失时插入密钥版本记录；从不更新或重新激活既有密钥。
     *
     * @param client 已连接客户端
     * @param tables 表元数据映射
     * @param plan   恢复语句收集器
     * @throws Exception 客户端失败时抛出
     */
    private static void appendKeyVersionPlan(RecoverClient client, Map<String, TableMeta> tables, List<String> plan)
            throws Exception {
        boolean missingTable = !tables.containsKey(KEY_VERSION_TABLE);
        if (missingTable || client.rows(KEY_VERSION_PROBE).isEmpty()) {
            plan.add(KEY_VERSION_INSERT);
        }
    }

    /**
     * 校验基础表类型、存储引擎，并拒绝需要人工复核的触发器与约束。
     *
     * @param client 已连接客户端
     * @param meta   表元数据
     * @param table  表名
     * @throws Exception 表不兼容或客户端失败时抛出
     */
    private static void checkBase(RecoverClient client, TableMeta meta, String table) throws Exception {
        if (!BASE_TABLE.equals(meta.type()) || !INNODB.equals(meta.engine())) {
            throw new RejectedException(table + ": expected InnoDB base table");
        }
        requireEmpty(client.rows(TRIGGERS_QUERY + table + "'"), table + ": triggers require manual review");
        requireEmpty(
                client.rows(CONSTRAINTS_QUERY + table + CONSTRAINTS_FILTER),
                table + ": CHECK/foreign key constraints require manual review");
    }

    /**
     * 校验单列定义、生成列属性与字符集。
     *
     * @param catalog  目录快照
     * @param table    表名
     * @param name     列名
     * @param expected 期望定义
     * @throws RejectedException 列不兼容时抛出
     */
    private static void checkField(SourceCatalog catalog, String table, String name, List<String> expected)
            throws RejectedException {
        ColumnMeta actual = catalog.column(name);
        if (actual == null || !actual.definition().equals(expected)) {
            throw new RejectedException(table + "." + name + ": incompatible column type/nullability/default/extra");
        }
        String generation = actual.generationExpression();
        if (generation != null && !generation.isEmpty()) {
            throw new RejectedException(table + "." + name + ": generated column");
        }
        String type = expected.get(0);
        boolean textual = type.startsWith(VARCHAR) || type.startsWith(TEXT);
        if (textual && !UTF8MB4.equals(actual.characterSet())) {
            throw new RejectedException(table + "." + name + ": expected utf8mb4");
        }
    }

    /**
     * 校验视图依赖的两列存在且为时间类型。
     *
     * @param catalog 目录快照
     * @param table   表名
     * @param name    列名
     * @throws RejectedException 列缺失或类型不兼容时抛出
     */
    private static void requireViewDependency(SourceCatalog catalog, String table, String name)
            throws RejectedException {
        ColumnMeta column = catalog.column(name);
        if (column == null) {
            throw new RejectedException(table + "." + name + ": missing/incompatible view dependency");
        }
        String type = column.type();
        if (!DATETIME.equals(type) && !TIMESTAMP.equals(type)) {
            throw new RejectedException(table + "." + name + ": missing/incompatible view dependency");
        }
    }

    /**
     * 要求表使用 utf8mb4 排序规则。
     *
     * @param meta  表元数据
     * @param table 表名
     * @throws RejectedException 排序规则不匹配时抛出
     */
    private static void requireUtf8mb4(TableMeta meta, String table) throws RejectedException {
        if (meta.collation() == null || !meta.collation().startsWith(UTF8MB4_PREFIX)) {
            throw new RejectedException(table + ": expected utf8mb4 table");
        }
    }

    /**
     * 要求查询结果为空。
     *
     * @param rows    查询结果
     * @param message 拒绝信息
     * @throws RejectedException 结果非空时抛出
     */
    private static void requireEmpty(List<List<Object>> rows, String message) throws RejectedException {
        if (!rows.isEmpty()) {
            throw new RejectedException(message);
        }
    }

    /**
     * 解析表元数据。
     *
     * @param client 已连接客户端
     * @return 表名到元数据的映射
     * @throws Exception 客户端失败时抛出
     */
    private static Map<String, TableMeta> tableMetadata(RecoverClient client) throws Exception {
        Map<String, TableMeta> tables = new LinkedHashMap<>();
        for (List<Object> row : client.rows(TABLES_QUERY)) {
            tables.put(
                    text(row.get(TABLE_NAME)),
                    new TableMeta(
                            text(row.get(TABLE_TYPE)), text(row.get(TABLE_ENGINE)), text(row.get(TABLE_COLLATION))));
        }
        return tables;
    }

    /**
     * 解析列元数据。
     *
     * @param client 已连接客户端
     * @return 列元数据集合
     * @throws Exception 客户端失败时抛出
     */
    private static List<ColumnMeta> columnMetadata(RecoverClient client) throws Exception {
        List<ColumnMeta> columns = new ArrayList<>();
        for (List<Object> row : client.rows(COLUMNS_QUERY)) {
            columns.add(new ColumnMeta(
                    text(row.get(COLUMN_TABLE)),
                    text(row.get(COLUMN_NAME)),
                    text(row.get(COLUMN_TYPE)),
                    text(row.get(COLUMN_NULLABLE)),
                    text(row.get(COLUMN_DEFAULT)),
                    text(row.get(COLUMN_EXTRA)),
                    text(row.get(COLUMN_GENERATION)),
                    text(row.get(COLUMN_CHARSET))));
        }
        return columns;
    }

    /**
     * 构建指定表的目录快照。
     *
     * @param columns   全部列元数据
     * @param indexRows 全部索引行
     * @param table     表名
     * @return 目录快照
     */
    private static SourceCatalog catalog(List<ColumnMeta> columns, List<List<Object>> indexRows, String table) {
        SourceCatalog catalog = new SourceCatalog();
        for (ColumnMeta column : columns) {
            if (table.equals(column.table())) {
                catalog.addColumn(column);
            }
        }
        for (List<Object> row : indexRows) {
            if (table.equals(text(row.get(INDEX_TABLE)))) {
                catalog.addIndex(text(row.get(INDEX_NAME)), new ArrayList<>(row.subList(INDEX_NON_UNIQUE, row.size())));
            }
        }
        return catalog;
    }

    /**
     * 解析单张契约表的字段、索引与重建 DDL。
     *
     * @param name 表名
     * @param body 建表语句括号内定义
     * @return 契约表
     * @throws RejectedException 出现无法识别的定义时抛出
     */
    private static SourceTable parseTable(String name, String body) throws RejectedException {
        SourceTable table =
                new SourceTable("CREATE TABLE " + name + " (" + body + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
        for (String raw : body.split("\n")) {
            String line = stripTrailingCommas(raw.strip());
            if (line.isEmpty()) {
                continue;
            }
            Matcher index = INDEX_CLAUSE_PATTERN.matcher(line);
            if (index.matches()) {
                table.getIndexes()
                        .put(index.group(1), new ArrayList<>(List.of(V42Contract.indexRow(1, index.group(2)))));
                continue;
            }
            Matcher field = FIELD_PATTERN.matcher(line);
            if (!field.lookingAt()) {
                throw new RejectedException("unrecognized V42 table definition: " + line);
            }
            String fieldName = field.group(1);
            String kind = normalizeKind(field.group(2));
            String rest = field.group(3);
            String value = defaultValue(rest);
            String extra = extra(rest, value);
            String nullable = rest.contains("NOT NULL") || rest.contains("PRIMARY KEY") ? "NO" : "YES";
            table.getFields().put(fieldName, V42Contract.field(kind, nullable, value, extra));
            if (rest.contains("PRIMARY KEY")) {
                table.getIndexes().put("PRIMARY", new ArrayList<>(List.of(V42Contract.indexRow(0, fieldName))));
            }
            if (rest.contains("UNIQUE")) {
                table.getIndexes().put(fieldName, new ArrayList<>(List.of(V42Contract.indexRow(0, fieldName))));
            }
        }
        return table;
    }

    /**
     * 去除行尾逗号，兼容多行定义。
     *
     * @param line 已裁剪空白的定义行
     * @return 去除尾逗号后的定义行
     */
    private static String stripTrailingCommas(String line) {
        String stripped = line;
        while (stripped.endsWith(",")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return stripped;
    }

    /**
     * 归一化列类型：BOOLEAN 在 MySQL 中落为 tinyint(1)。
     *
     * @param kind 原始类型
     * @return 归一化后的类型
     */
    private static String normalizeKind(String kind) {
        String normalized = kind.toLowerCase(Locale.ROOT);
        return BOOLEAN.equals(normalized) ? TINYINT_ONE : normalized;
    }

    /**
     * 解析默认值，剥离成对引号并把布尔字面量映射为 1/0。
     *
     * @param rest 类型之后的定义片段
     * @return 默认值；未声明时为 null
     */
    private static String defaultValue(String rest) {
        Matcher defaults = DEFAULT_PATTERN.matcher(rest);
        if (!defaults.find()) {
            return null;
        }
        String value = defaults.group(1);
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            value = value.substring(1, value.length() - 1);
        }
        if ("TRUE".equals(value)) {
            return "1";
        }
        if ("FALSE".equals(value)) {
            return "0";
        }
        return value;
    }

    /**
     * 计算 information_schema 的 extra 语义。
     *
     * @param rest  类型之后的定义片段
     * @param value 默认值
     * @return extra 取值
     */
    private static String extra(String rest, String value) {
        if (rest.contains("AUTO_INCREMENT")) {
            return "auto_increment";
        }
        return "CURRENT_TIMESTAMP".equals(value) ? "DEFAULT_GENERATED" : "";
    }

    /**
     * 定位 V42 迁移源文件。
     *
     * @return 迁移脚本路径
     */
    private static Path sourcePath() {
        String root = System.getProperty(LocalDeployment.ROOT_PROPERTY);
        if (root != null) {
            return Path.of(root).resolve(V42_SOURCE);
        }
        try {
            Path base = Path.of(TokenV42Recover.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            for (int level = 0; level < SOURCE_PARENT_LEVELS; level++) {
                base = base.getParent();
                if (base == null) {
                    throw new IllegalStateException("无法定位 V42 迁移源文件");
                }
            }
            return base.resolve(V42_SOURCE);
        } catch (Exception error) {
            throw new IllegalStateException("无法定位 V42 迁移源文件", error);
        }
    }

    /**
     * 按 {@code --name value} 形式解析命令行。
     *
     * @param args 命令行参数
     * @return 解析结果
     */
    private static Options parse(String[] args) {
        String database = null;
        boolean offline = false;
        boolean apply = false;
        List<String> command = null;
        Mode mode = Mode.VALID;
        for (int index = 0; index < args.length && mode == Mode.VALID; index++) {
            String arg = args[index];
            if (DATABASE_OPTION.equals(arg)) {
                if (index + 1 >= args.length) {
                    mode = Mode.INVALID;
                } else {
                    index++;
                    database = args[index];
                }
            } else if (arg.startsWith(DATABASE_PREFIX)) {
                database = arg.substring(DATABASE_PREFIX.length());
            } else if (OFFLINE_OPTION.equals(arg)) {
                offline = true;
            } else if (APPLY_OPTION.equals(arg)) {
                apply = true;
            } else if (MYSQL_COMMAND_OPTION.equals(arg)) {
                command = new ArrayList<>(List.of(args).subList(index + 1, args.length));
                index = args.length;
            } else if (arg.startsWith(MYSQL_COMMAND_PREFIX)) {
                command = new ArrayList<>();
                command.add(arg.substring(MYSQL_COMMAND_PREFIX.length()));
                command.addAll(List.of(args).subList(index + 1, args.length));
                index = args.length;
            } else if (HELP_OPTION.equals(arg) || HELP_SHORT_OPTION.equals(arg)) {
                mode = Mode.HELP;
            } else {
                mode = Mode.INVALID;
            }
        }
        return new Options(mode, database, offline, apply, command);
    }

    /**
     * 校验必填参数与取值范围。
     *
     * @param database 目标库名
     * @param offline  是否声明离线
     * @param command  mysql 客户端命令
     * @return 合法时返回 true
     */
    private static boolean valid(String database, boolean offline, List<String> command) {
        if (!offline || database == null) {
            return false;
        }
        if (!DATABASE_PATTERN.matcher(database).matches()) {
            return false;
        }
        return !command.isEmpty();
    }

    /**
     * 判断 mysql 客户端参数是否强制包含 {@code --force}；该选项会掩盖结构错误。
     *
     * @param option 单个客户端参数
     * @return 禁止使用时返回 true
     */
    private static boolean forbidden(String option) {
        String name = option.split("=", 2)[0];
        return FORCE_OPTION.equals(name) || SHORT_FORCE_PATTERN.matcher(option).find();
    }

    /**
     * 输出使用说明并返回参数错误退出码。
     *
     * @param err 标准错误
     * @return 退出码
     */
    private static int usage(PrintWriter err) {
        err.println(USAGE);
        return EXIT_USAGE;
    }

    /**
     * 生成计划输出 JSON，格式与 Python 版本逐字一致。
     *
     * @param apply 是否为 apply 模式
     * @param plan  恢复语句
     * @return JSON 文本
     */
    private static String jsonPlan(boolean apply, List<String> plan) {
        StringBuilder json = new StringBuilder("{\"mode\": \"")
                .append(apply ? "apply" : "plan")
                .append("\", \"statements\": [");
        for (int index = 0; index < plan.size(); index++) {
            if (index > 0) {
                json.append(", ");
            }
            json.append(jsonString(plan.get(index)));
        }
        return json.append("]}").toString();
    }

    /**
     * 转义 JSON 字符串字面量。
     *
     * @param text 原始文本
     * @return 带引号的 JSON 字符串
     */
    private static String jsonString(String text) {
        StringBuilder json = new StringBuilder("\"");
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            switch (value) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\t' -> json.append("\\t");
                case '\r' -> json.append("\\r");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                default -> {
                    if (value < CONTROL_LIMIT) {
                        json.append(String.format("\\u%04x", (int) value));
                    } else {
                        json.append(value);
                    }
                }
            }
        }
        return json.append("\"").toString();
    }

    /**
     * 计算 SHA-256 十六进制摘要。
     *
     * @param text 文本
     * @return 小写十六进制摘要
     */
    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    /**
     * 规范化片段：引号标识符仅去引号并小写，其余去除空白并小写。
     *
     * @param part        片段
     * @param quotedToken 是否为引号片段
     * @return 规范化片段
     */
    private static String normalizePart(String part, boolean quotedToken) {
        if (quotedToken) {
            if (SIMPLE_IDENTIFIER_PATTERN.matcher(part).matches()) {
                return part.substring(1, part.length() - 1).toLowerCase(Locale.ROOT);
            }
            return part;
        }
        return WHITESPACE_PATTERN.matcher(part).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /**
     * 将 JSON 标量安全转换为字符串。
     *
     * @param value 原始值
     * @return 字符串；null 原样返回
     */
    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
