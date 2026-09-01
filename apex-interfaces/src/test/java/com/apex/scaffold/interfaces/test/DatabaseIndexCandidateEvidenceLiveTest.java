package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * 第二批条件性索引的目标 MySQL 只读证据检查。
 *
 * <p>默认跳过，只有显式设置 {@code -Dapex.index.candidate-evidence.enabled=true} 时才连接目标数据库。
 *
 * @author Apex Team
 */
@EnabledIfSystemProperty(named = "apex.index.candidate-evidence.enabled", matches = "true")
class DatabaseIndexCandidateEvidenceLiveTest {

    private static final Map<String, String> GROUP_MAX_QUERIES = groupMaxQueries();

    /**
     * 输出候选表规模、热点分组规模和拟新增索引冲突，不执行 DDL/DML。
     *
     * @throws Exception 连接或查询失败时抛出
     */
    @Test
    void shouldCollectEvidenceForConditionalIndexes() throws Exception {
        String yaml = Files.readString(projectRoot().resolve("apex-interfaces/src/main/resources/application-dev.yml"));

        try (Connection connection = DriverManager.getConnection(
                property(yaml, "DB_MASTER_URL"),
                property(yaml, "DB_MASTER_USERNAME"),
                property(yaml, "DB_MASTER_PASSWORD"))) {
            connection.setReadOnly(true);

            System.out.println(
                    "database_identity=" + row(connection, "SELECT DATABASE(),@@hostname,@@version,@@read_only"));
            System.out.println("latest_flyway="
                    + row(
                            connection,
                            "SELECT version,description,success FROM flyway_schema_history "
                                    + "WHERE success = 1 AND type <> 'DELETE' "
                                    + "ORDER BY installed_rank DESC LIMIT 1"));

            for (String table : GROUP_MAX_QUERIES.keySet()) {
                long exactRows = number(connection, "SELECT COUNT(*) FROM `" + table + "`");
                long groupedMaximum = number(connection, GROUP_MAX_QUERIES.get(table));
                String storage = row(
                        connection,
                        "SELECT data_length,index_length FROM information_schema.tables "
                                + "WHERE table_schema=DATABASE() AND table_name='" + table + "'");
                System.out.println("candidate_table=" + table + "|rows=" + exactRows + "|max_group_rows="
                        + groupedMaximum + "|bytes=" + storage);
            }

            System.out.println("explain_photo_recent="
                    + row(
                            connection,
                            "EXPLAIN SELECT id FROM biz_photo "
                                    + "WHERE status='PUBLISHED' AND deleted=0 "
                                    + "ORDER BY published_at DESC,id DESC LIMIT 20"));
            System.out.println("explain_photo_type="
                    + row(
                            connection,
                            "EXPLAIN SELECT id FROM biz_photo "
                                    + "WHERE photo_type='PORTRAIT_CLOSE' AND deleted=0 "
                                    + "ORDER BY create_time DESC,id DESC LIMIT 20"));
            System.out.println("explain_photo_angle="
                    + row(
                            connection,
                            "EXPLAIN SELECT id FROM biz_photo "
                                    + "WHERE angle='FRONTAL' AND deleted=0 "
                                    + "ORDER BY create_time DESC,id DESC LIMIT 20"));

            long candidateIndexConflicts = number(
                    connection,
                    "SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) "
                            + "FROM information_schema.statistics "
                            + "WHERE table_schema=DATABASE() AND index_name IN ("
                            + "'idx_photo_status_publish_time','idx_photo_type_time',"
                            + "'idx_photo_angle_time','idx_favorite_user_timeline',"
                            + "'idx_report_owner_type_time','idx_subscription_user_active',"
                            + "'idx_face_dedup_user_status_time','uk_onboarding_user_scene_question',"
                            + "'idx_blacklist_user_timeline','idx_policy_type_time')");
            System.out.println("candidate_index_name_conflicts=" + candidateIndexConflicts);

            assertThat(GROUP_MAX_QUERIES).hasSize(10);
            assertThat(candidateIndexConflicts).as("第二批候选索引同名冲突数").isZero();
        }
    }

    /**
     * 构造各业务表分组统计最大行数的 SQL 映射。
     *
     * @return 表名到分组最大值查询 SQL 的有序映射
     */
    private static Map<String, String> groupMaxQueries() {
        Map<String, String> queries = new LinkedHashMap<>();
        queries.put(
                "biz_photo",
                "SELECT COALESCE(MAX(group_rows),0) FROM ("
                        + "SELECT COUNT(*) group_rows FROM biz_photo "
                        + "GROUP BY status,photo_type,angle) grouped_rows");
        queries.put(
                "biz_favorite",
                "SELECT COALESCE(MAX(group_rows),0) FROM ("
                        + "SELECT COUNT(*) group_rows FROM biz_favorite GROUP BY user_id) grouped_rows");
        queries.put(
                "biz_report",
                "SELECT COALESCE(MAX(group_rows),0) FROM ("
                        + "SELECT COUNT(*) group_rows FROM biz_report "
                        + "GROUP BY owner_user_id,report_type) grouped_rows");
        queries.put(
                "biz_subscription",
                "SELECT COALESCE(MAX(group_rows),0) FROM ("
                        + "SELECT COUNT(*) group_rows FROM biz_subscription GROUP BY user_id) grouped_rows");
        queries.put(
                "biz_face_dedup_record",
                "SELECT COALESCE(MAX(group_rows),0) FROM ("
                        + "SELECT COUNT(*) group_rows FROM biz_face_dedup_record GROUP BY user_id) grouped_rows");
        queries.put(
                "biz_onboarding_guide_answer",
                "SELECT COALESCE(MAX(group_rows),0) FROM ("
                        + "SELECT COUNT(*) group_rows FROM biz_onboarding_guide_answer "
                        + "GROUP BY user_id,scene) grouped_rows");
        queries.put(
                "biz_blacklist",
                "SELECT COALESCE(MAX(group_rows),0) FROM ("
                        + "SELECT COUNT(*) group_rows FROM biz_blacklist GROUP BY user_id) grouped_rows");
        queries.put(
                "biz_policy_version",
                "SELECT COALESCE(MAX(group_rows),0) FROM ("
                        + "SELECT COUNT(*) group_rows FROM biz_policy_version "
                        + "GROUP BY policy_type) grouped_rows");
        queries.put(
                "sys_api_call_log",
                "SELECT COALESCE(MAX(group_rows),0) FROM ("
                        + "SELECT COUNT(*) group_rows FROM sys_api_call_log "
                        + "GROUP BY service_name,status) grouped_rows");
        queries.put(
                "biz_chat",
                "SELECT COALESCE(MAX(group_rows),0) FROM ("
                        + "SELECT COUNT(*) group_rows FROM biz_chat GROUP BY user_a) grouped_rows");
        return queries;
    }

    /**
     * 解析配置值：优先取环境变量，否则从 YAML 占位符中提取默认值。
     *
     * @param yaml 数据库配置 YAML 内容
     * @param name 配置项名称
     * @return 解析后的配置值
     * @throws IllegalStateException 环境变量与 YAML 中均未配置时抛出
     */
    private String property(String yaml, String name) {
        String environmentValue = System.getenv(name);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }
        Matcher matcher = Pattern.compile("\\$\\{" + name + ":([^}]*)}").matcher(yaml);
        if (!matcher.find()) {
            throw new IllegalStateException("缺少数据库配置: " + name);
        }
        return matcher.group(1);
    }

    /**
     * 执行单值查询 SQL 并返回首行首列数值。
     *
     * @param connection 数据库连接
     * @param sql        单值查询 SQL
     * @return 查询结果数值
     * @throws Exception 查询或断言失败时抛出
     */
    private long number(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    /**
     * 执行查询 SQL 并将首行各列以竖线拼接返回。
     *
     * @param connection 数据库连接
     * @param sql        查询 SQL
     * @return 首行各列拼接后的字符串
     * @throws Exception 查询或断言失败时抛出
     */
    private String row(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            StringBuilder value = new StringBuilder();
            for (int column = 1; column <= resultSet.getMetaData().getColumnCount(); column++) {
                if (column > 1) {
                    value.append('|');
                }
                value.append(resultSet.getString(column));
            }
            return value.toString();
        }
    }

    /**
     * 定位多模块工程根目录。
     *
     * @return 包含 apex-interfaces 模块的工程根目录
     */
    private Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir"));
        if (Files.isDirectory(current.resolve("apex-interfaces"))) {
            return current;
        }
        return current.getParent();
    }
}
